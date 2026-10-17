# Privacy and data handling

Oblako is a direct client for a Nextcloud server selected by the user. The project does not run a relay, synchronization proxy, analytics endpoint, advertising service, or account database.

## Data that stays on the Android device

- the selected Nextcloud server address;
- account identity returned by Nextcloud;
- the dedicated Nextcloud app password, encrypted with a key protected by Android Keystore;
- a Room metadata index used to render and synchronize the gallery;
- thumbnail and downloaded-file caches;
- upload queue, synchronization preferences, and selected device folders;
- theme and interface preferences.

## Data sent over the network

The app sends requests only to the Nextcloud base URL entered by the user, plus the browser application chosen by Android for the Nextcloud Login Flow. Photographs, videos, credentials, and metadata are not sent to an Oblako-operated service.

## Android permissions

Internet access is required to communicate with Nextcloud. Photo and video access is requested only for local browsing or user-enabled uploads. Notification and foreground-service permissions are used to display durable transfer progress. Network permissions support Wi-Fi-only synchronization policy.

## Removing local data

Signing out asks Nextcloud to revoke the app password and removes the local session. Android application storage can be removed completely from **System Settings → Apps → Oblako → Storage → Clear storage**, or by uninstalling the app.

Installing an APK over an existing Oblako installation does not automatically erase Android application data. This is standard Android behavior.
