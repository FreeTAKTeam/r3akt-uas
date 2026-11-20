# Picture marker: cross-repository contract (proposal)

Status: **agreed with RCH, 17 November 2026**, for [r3akt-uas #19](https://github.com/FreeTAKTeam/r3akt-uas/issues/19) (UAS-OBS-005). The RCH side is [RCH #343](https://github.com/FreeTAKTeam/Reticulum-Community-Hub/pull/343) (commit `82f72e97`, merged as `577e4667`, which is pinned here); the REM side is [REM #351](https://github.com/FreeTAKTeam/reticulum_mobile_emergency_management/pull/351) (commit `4a5673c8`, merged as `48d60acd`) (gap G-25). The encoder belongs to `r3akt-profile-rch` (RCH repository); this repository only calls it (AGENTS.md ownership rule). Byte caps and JPEG quality are provisional until measured (issue acceptance test 7).

## Image tiers

| Tier | Size | Where it goes |
|---|---|---|
| Original | sensor resolution | Stays on the aircraft's card and the phone. Never sent. |
| Reduced | 1600 × 1200 JPEG, 4:3 | Uploaded to RCH only (message 2). REM fetches it on request (message 3). Never fanned out. |
| Thumbnail | 160 × 120 JPEG, 4:3, target about 2 KB | Inside the marker message (message 1). RCH relays it to the team with the marker. |

Both crops are 4:3 around the aim cross (frame centre). An AI box may choose the crop only under issue #19's rules (recent, matching frame, one overlapping box); the marker position stays the gimbal aim point either way.

## Message 1: picture marker (drone to RCH, then RCH to the team)

One LXMF message, built by one shared encoder call (proposed name `r3akt_profile_rch::encode_commands_for_team_with_image`):

- **Field 9**: `mission.marker.create`, the same args as an ordinary marker, with `marker_type` = `symbol` = `category` = `"picture"` (a new map-core catalog entry) and one extra object:
  ```json
  "picture": {
    "thumbnail": {"media_type": "image/jpeg", "width": 160, "height": 120, "sha256": "<hex>"},
    "captured_at": "<ISO 8601 UTC>",
    "camera": {"lat": 0.0, "lon": 0.0, "altitude_m": 0.0, "altitude_reference": "takeoff",
               "heading_deg": 0.0, "gimbal_pitch_deg": 0.0, "gimbal_yaw_deg": 0.0},
    "crop": "centre"
  }
  ```
  Unknown camera values are omitted, never guessed. `crop` is `"centre"` or `"ai_box"`.
- **Field 11**: the team UID, as for every marker.
- **Field 6** (`FIELD_IMAGE`, standard LXMF): `["jpg", <thumbnail bytes>]`.

The marker's **picture ID is this message's LXMF message ID** (hash of the finished message). There is no separate capture ID. Message 1 never refers to message 2.

RCH stores the marker as usual, stores the thumbnail linked to the marker's `object_destination_hash` and the picture ID, and adds `picture` (with `"message_id": "<hex>"`) to the marker's `api_value`. Its `mission.marker.created` relay to the team carries the same field 6. Delivery of the thumbnail never waits for message 2.

## Message 2: reduced image upload (drone to RCH only)

Sent right after message 1 is in the custody outbox, over the same TCP link, delivered as a Resource:

- **Field 9**: `mission.marker.picture.upload` with args
  `{"object_destination_hash", "message_id": "<picture ID>", "media_type": "image/jpeg", "width": 1600, "height": 1200, "sha256": "<hex>"}`.
- **Field 6**: `["jpg", <reduced image bytes>]`.
- **No field 11**: RCH stores it and relays it to nobody.

RCH checks the sender is the marker's writer, checks the hash, and stores the image under the picture ID with a manifest row (picture ID → sha256, size, media type, stored path). An upload that arrives before message 1 is held or refused, never attached to the wrong marker.

## Message 3: reduced image retrieval (REM to RCH, RCH answers the requester only)

- REM sends field 9 `mission.marker.picture.get` with `{"message_id": "<picture ID>"}`.
- RCH answers the requester only with a field 9 result whose status is `ok` (and field 6 `["jpg", bytes]`, sha256 in the result), `pending` (no upload yet), `unavailable` or `unauthorized` (same team rules as the marker).

## REM display

- `marker_type == "picture"` with a valid thumbnail: draw the thumbnail itself as the marker, bounded in size, with a visible anchor at the coordinate.
- Missing or invalid thumbnail: an explicit picture fallback marker at the same place. Other marker types are unchanged.
- Selecting it: larger preview and a "load full image" action (message 3) showing pending/unavailable.
- Text never claims the photographed object is at the aim point.

## As built by RCH #343

- Replies to upload and get come in **field 10** (`FIELD_RESULTS`), like every RCH command reply; the status is `result.status`.
- `object_destination_hash` is required on a picture create; RCH refuses a create whose field-6 thumbnail does not match `picture.thumbnail.sha256`.
- An upload that arrives before message 1 is stored is **refused** (`unknown_picture`), not held. The app's outbox delivers the oldest first, ordered by admission second then transient ID, so the app admits the upload one second after message 1 and message 1 always goes first (issue #21). The upload is deposited first: if message 1 cannot be deposited, nothing is reported placed and the lone upload is refused by the hub. The app reads the hub's refusal of any of its commands (field 10, command IDs starting `uas-`) and shows it on the MESH status; a refused upload is not retried.
- Cancelling the panel while the camera answers drops the frame: nothing is shared, and a photo the camera already took stays on its card (issue #22).
- Upload refusals: `unauthorized` (not the marker's writer), `invalid_payload`, `unknown_picture`, `conflict`. Get: `ok` (with field 6), `pending`, `unavailable`, `unauthorized` (`mission.content.read`, and the team check when field 11 is sent).
- REM decode: `r3akt_map_core::marker_picture::decode_relay(fields_json, local_id)`, which drops a thumbnail whose hash does not match.
- Known RCH gaps: deleting a marker keeps its pictures; the RCH web UI has no `picture` icon.

## Order of work

1. RCH: catalog entry, shared encoder (and its decode for REM), hub store/relay of message 1, messages 2 and 3. Then this repository re-pins RCH.
2. REM: decode and render; message 3. Needs RCH step 1's decode helpers.
3. This repository: capture, crops and the local pending marker work now; sending waits for the re-pin.
