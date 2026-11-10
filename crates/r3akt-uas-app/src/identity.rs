//! App Reticulum identity and aircraft object id (decisions D-2 and D-3).
//!
//! Both live in one private directory through the shared `FileKeyManager`. The
//! private key never leaves this module; callers see only the `lxmf.delivery`
//! destination hash and the object id.

use std::fmt;
use std::path::Path;

use rand_core::{OsRng, RngCore};
use rns_core::key_manager::{FileKeyManager, KeyManagerBackend, KeyPurpose, StoredKey};
use rns_transport::destination::{DestinationName, SingleInputDestination};
use rns_transport::identity::PrivateIdentity;

const IDENTITY_KEY: &str = "app-identity";
const OBJECT_KEY: &str = "aircraft-object-id";

#[derive(Debug, thiserror::Error)]
pub enum StoreError {
    /// The shared key store failed; the text names the operation, never key material.
    #[error("identity store failed to {0}")]
    Io(&'static str),
    #[error("identity store is inconsistent: {0}")]
    Corrupt(&'static str),
}

/// The app identity and the aircraft object it publishes.
pub struct AppIdentity {
    identity: PrivateIdentity,
    object_id: String,
}

impl fmt::Debug for AppIdentity {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("AppIdentity")
            .field("destination_hash", &self.destination_hash())
            .field("object_id", &self.object_id)
            .finish_non_exhaustive()
    }
}

impl AppIdentity {
    /// Loads the identity from `dir`, generating identity and object id on first run.
    ///
    /// # Errors
    /// [`StoreError::Io`] when the store cannot be read or written, and
    /// [`StoreError::Corrupt`] when an identity exists without its object id or
    /// a stored value is malformed; a new object id is then never invented,
    /// because it would create a second aircraft object on the hub.
    pub fn load_or_create(dir: &Path) -> Result<Self, StoreError> {
        let store = FileKeyManager::new(dir).map_err(|_| StoreError::Io("open the directory"))?;
        let stored = store
            .get(IDENTITY_KEY)
            .map_err(|_| StoreError::Io("read the identity"))?;
        let Some(key) = stored else {
            return Self::create(&store);
        };
        let identity = PrivateIdentity::from_private_key_bytes(&key.material)
            .map_err(|_| StoreError::Corrupt("identity key is malformed"))?;
        let object = store
            .get(OBJECT_KEY)
            .map_err(|_| StoreError::Io("read the object id"))?
            .ok_or(StoreError::Corrupt("identity has no object id"))?;
        let object_id = String::from_utf8(object.material)
            .ok()
            .filter(|id| {
                id.len() == 32 && id.bytes().all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f'))
            })
            .ok_or(StoreError::Corrupt("object id is malformed"))?;
        Ok(Self {
            identity,
            object_id,
        })
    }

    fn create(store: &FileKeyManager) -> Result<Self, StoreError> {
        let mut bytes = [0u8; 16];
        OsRng.fill_bytes(&mut bytes);
        let object_id = to_hex(&bytes);
        let identity = PrivateIdentity::new_from_rand(OsRng);
        // Object id first: the identity's presence marks a complete store.
        store
            .put(StoredKey {
                key_id: OBJECT_KEY.to_owned(),
                purpose: KeyPurpose::Custom(OBJECT_KEY.to_owned()),
                material: object_id.clone().into_bytes(),
            })
            .map_err(|_| StoreError::Io("write the object id"))?;
        store
            .put(StoredKey {
                key_id: IDENTITY_KEY.to_owned(),
                purpose: KeyPurpose::IdentitySigning,
                material: identity.to_private_key_bytes().to_vec(),
            })
            .map_err(|_| StoreError::Io("write the identity"))?;
        Ok(Self {
            identity,
            object_id,
        })
    }

    /// The announced `lxmf.delivery` destination hash, lowercase hex.
    #[must_use]
    pub fn destination_hash(&self) -> String {
        SingleInputDestination::new(
            self.identity.clone(),
            DestinationName::new("lxmf", "delivery"),
        )
        .desc
        .address_hash
        .to_hex_string()
    }

    /// The app-chosen aircraft object id (`object_destination_hash`), 32 lowercase hex.
    #[must_use]
    pub fn object_id(&self) -> &str {
        &self.object_id
    }

    pub(crate) fn private_identity(&self) -> &PrivateIdentity {
        &self.identity
    }
}

fn to_hex(bytes: &[u8]) -> String {
    use std::fmt::Write;
    bytes.iter().fold(String::new(), |mut out, b| {
        let _ = write!(out, "{b:02x}");
        out
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicU32, Ordering};

    fn temp_dir() -> std::path::PathBuf {
        static N: AtomicU32 = AtomicU32::new(0);
        let dir = std::env::temp_dir().join(format!(
            "r3akt-uas-identity-{}-{}",
            std::process::id(),
            N.fetch_add(1, Ordering::Relaxed)
        ));
        let _ = std::fs::remove_dir_all(&dir);
        dir
    }

    #[test]
    fn reload_gives_same_destination_and_object() {
        let dir = temp_dir();
        let first = AppIdentity::load_or_create(&dir).unwrap();
        let again = AppIdentity::load_or_create(&dir).unwrap();
        assert_eq!(first.destination_hash(), again.destination_hash());
        assert_eq!(first.object_id(), again.object_id());
        assert_eq!(first.destination_hash().len(), 32);
        assert_ne!(first.destination_hash(), first.object_id());
        let other = AppIdentity::load_or_create(&temp_dir()).unwrap();
        assert_ne!(first.destination_hash(), other.destination_hash());
        assert_ne!(first.object_id(), other.object_id());
    }

    #[test]
    fn private_key_never_in_debug_or_errors() {
        let dir = temp_dir();
        let id = AppIdentity::load_or_create(&dir).unwrap();
        let key = id.private_identity().to_private_key_bytes();
        let hex = to_hex(&key);
        let debug = format!("{id:?}");
        assert!(!debug.contains(&hex) && !debug.contains(&format!("{key:?}")));

        // Identity without object id is refused, and the error names no key bytes.
        std::fs::remove_file(dir.join(format!("{OBJECT_KEY}.key"))).unwrap();
        let err = AppIdentity::load_or_create(&dir).unwrap_err();
        assert!(matches!(err, StoreError::Corrupt(_)));
        for text in [err.to_string(), format!("{err:?}")] {
            assert!(!text.contains(&hex) && !text.contains(&format!("{key:?}")));
        }
    }
}
