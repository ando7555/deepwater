# Email (Postmark or Proton Mail) and owner notifications (Signal)

Status: implemented behind settings, covered by unit tests. **Not yet tried against a real Proton account or a real
Signal number.** Everything is off or unchanged until the variables below are set on the Railway backend.

## How it is built

- Business code only sees two small interfaces: `EmailSender` (platform) and `Notifier` (notify).
- The provider is picked from environment variables at start-up; switching provider needs no code change.
- Sign-up and Amber reports publish plain Spring events (`AccountRegistered`, `AmberReportFiled`).
  `OwnerNotifications` listens, waits for the database commit, and sends on a background virtual thread,
  so a slow or broken Signal service never delays a sign-up.
- Notification texts carry **no names, emails or report notes**, because they leave Deepwater for a third party.

## Email

| Variable | Meaning | Default |
|---|---|---|
| `MAIL_PROVIDER` | `postmark`, `smtp` (Proton Mail or any SMTP server) or `none` | `postmark` (today's behaviour) |
| `MAIL_FROM` | Sender address | empty = nothing is sent |
| `POSTMARK_SERVER_TOKEN` | Postmark only | empty |
| `SMTP_HOST` / `SMTP_PORT` | SMTP server | `smtp.protonmail.ch` / `587` |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | SMTP login | empty |
| `SMTP_STARTTLS` | Require STARTTLS | `true` |

### Proton Mail setup (owner)

Proton has no public sending API. It offers **SMTP submission** with a token
([Proton support: SMTP submission](https://proton.me/support/smtp-submission)):

1. A paid Proton Mail plan **with a custom domain** (for example `hello@your-domain`). Addresses ending in
   `@proton.me`, `@protonmail.com` or `@pm.me` cannot use SMTP. Proton's pages disagree on which paid plans qualify
   (some say all paid plans, older text says business, Visionary and Family): check your own settings.
2. Proton: Settings → All settings → IMAP/SMTP → SMTP tokens → generate a token for that custom-domain address.
3. Railway backend variables: `MAIL_PROVIDER=smtp`, `MAIL_FROM=hello@your-domain`,
   `SMTP_USERNAME=hello@your-domain`, `SMTP_PASSWORD=<the token>`. Host and port defaults already point at Proton.
4. Railway must allow outbound connections to `smtp.protonmail.ch:587`. Not verified yet.

Never paste the token in chat; put it straight into Railway variables. Proton Bridge is not used: it would need a
desktop app running next to the backend.

## Signal notifications

Signal has **no official bot or business API**. The only workable route is the unofficial, open-source
[signal-cli-rest-api](https://github.com/bbernhard/signal-cli-rest-api) (a wrapper around signal-cli) running as its
own service. Deepwater calls `POST {SIGNAL_API_URL}/v2/send`.

| Variable | Meaning | Default |
|---|---|---|
| `NOTIFY_CHANNEL` | `signal` or `none` | `none` |
| `NOTIFY_EVENTS` | Comma list: `signup`, `amber-report` | `signup,amber-report` |
| `SIGNAL_API_URL` | Where the signal-cli-rest-api service runs | empty |
| `SIGNAL_SENDER` | The Signal number that sends, e.g. `+45...` | empty |
| `SIGNAL_RECIPIENTS` | Comma list of numbers that receive | empty |

First scope (proposal): notify **only the owner**, for a new sign-up and a new Amber report waiting for review.
Notifying learners by Signal would need their phone number, an explicit opt-in and an opt-out; not built.

### Owner setup

1. Run the `bbernhard/signal-cli-rest-api` container as a second Railway service with a persistent volume
   on `/home/.local/share/signal-cli` (or locally: `docker compose --profile signal up signal-api`).
   Keep it private: Railway private networking, no public domain, because the API has no login of its own.
2. Give it a number: either link it as a second device of your own Signal (QR code from `/v1/qrcodelink`) or
   register a separate number (a spare SIM or VoIP number that can receive an SMS or call).
3. Railway backend variables: `NOTIFY_CHANNEL=signal`, `SIGNAL_API_URL=http://<private service host>:8080`,
   `SIGNAL_SENDER=+...`, `SIGNAL_RECIPIENTS=+...`.

Risks: unofficial client, so Signal protocol changes can break it until the image is updated; Signal can
rate-limit or block numbers that look automated; a linked device stops working if unused for a long time.
Pin a tested image version instead of `latest` once it works.
