# Privacy

Faden has no server, no account of its own, no analytics and no ads. It talks to Reddit for you, and to a few other
sites when the content you're looking at comes from them. This page lists every place it connects to and everything
it keeps.

## What stays on your phone

Apart from media you download (saved to your Pictures and Movies folders) and backups you save yourself, everything
Faden stores is in its private app storage, which other apps can't read:

- your settings, themes, filters, user tags, drafts and the communities and posts you watch;
- your history: posts you've read or opened, recent posts and searches in Explore;
- for each Reddit account you log into, its name and a Reddit **refresh token** (what keeps you logged in), plus a
  short-lived access token for the active account;
- the Reddit API setup you entered (client ID, redirect URI, user agent);
- a random ID, made on first use, that Faden sends Reddit when you browse logged out (Reddit asks apps for a stable
  device ID with those requests);
- cached images and videos (videos up to 100 MB), which Android may clear at any time;
- the last crash report, if the app crashed;
- after you restore a backup, a copy of everything as it was before (accounts included), so the restore can be
  undone. It's deleted after 7 days, or as soon as you log out.

Android's cloud backup is turned off for Faden. When you move to a new phone, Android's device-to-device transfer
can copy your settings and history, but never your logins: you log in again on the new phone.

Backups you make yourself in Settings › Backup are files you choose where to save. Settings, history, the API setup
and account names are in plain text in them. Logins (refresh tokens) go in only if you set a password, and are then
encrypted (AES-256-GCM).

Crash reports never leave your phone unless you tap Share or Copy on the crash screen. A report holds the stack
trace, the Faden and Android versions, and your phone's make, model and screen size.

## Who Faden connects to

| Site | When | What it gets |
|---|---|---|
| **Reddit** (`reddit.com`, `oauth.reddit.com`, `redd.it` and its image and video hosts) | Whenever you use the app, and in the background if you turn on message checks or watch a community or post | Everything a Reddit app needs: what you read, vote, post and search, under your account if you're logged in. Reddit's own [privacy policy](https://www.reddit.com/policies/privacy-policy) applies. |
| **GitHub** (`api.github.com`) | About twice a day (sooner after a failed check), if Settings › Everything else › Check for updates is on (it is by default) | A request for Faden's latest release. No account information. |
| **RedGifs** (`api.redgifs.com` and its video hosts) | When you open or download a RedGifs clip | The clip you asked for. |
| **imgur and Giphy** (`i.imgur.com`, `i.giphy.com`, `media.giphy.com`) | When a comment or link card with one of their images is on screen (Settings › Link handling › Pictures in comments and Link cards turn these off) | The image's address. |
| **Any other site an image, GIF or video comes from** | Only when you tap the link to open it | The image's address. |
| **Wikimedia** (`upload.wikimedia.org`) | When you open Settings › View type, for its sample image | That one image. |
| **Google Translate** | Only when you tap Translate on a post | The post's title. |
| **Ko-fi** | Only when you tap Support Faden | Nothing from Faden; it just opens the page in your browser. |

Like any connection, each of these sees your IP address.

Links you tap that Faden has no page for open in your browser, which then follows its own privacy settings.

## Deleting your data

Logging out of an account (the Log out button beside your name in the account sheet) deletes its tokens from your
phone, along with the pre-restore copy described above, if there is one. To revoke Faden's access on
Reddit's side as well, remove the app under [reddit.com/prefs/apps](https://www.reddit.com/prefs/apps). Uninstalling
Faden deletes everything it stored, except backups and downloaded media you saved to your own folders.
