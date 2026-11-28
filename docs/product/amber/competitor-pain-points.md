# Amber vs. Tinder, Bumble, Hinge: what people dislike, and what Amber does instead

Owner ask (2026-11-27, Polish): "check all the minuses of Tinder, Bumble, Hinge and so on, build something
important, better and new."

This is desk research from public sources, not user research. Every claim below either has a source or is
marked as unverified. Nothing here proves that Amber's answers work: that needs a test with real people.

## What people complain about

| # | Pain | Evidence | Status |
|---|---|---|---|
| 1 | Burnout from endless swiping | Forbes Health survey (2024, 1,000 US app users): 78% feel dating-app fatigue at least sometimes; average 51 minutes a day swiping. 2025 follow-up: over half of Gen Z users feel burned out often or always. ([ABC News/AP](https://abcnews.go.com/Health/wireStory/feel-exhausted-swiping-dating-apps-experiencing-burnout-111507292), [AOL](https://www.aol.com/articles/why-gen-z-leaving-dating-170550511.html)) | Sourced |
| 2 | Harassment and unwanted explicit images, mostly aimed at women | Pew Research (2019 survey, published 2020): 57% of women users aged 18–34 received an explicit message or image they did not ask for; 60% were contacted again after saying no. ([Pew](https://www.pewresearch.org/short-reads/2020/03/06/young-women-often-face-sexual-harassment-online-including-on-dating-sites-and-apps/)) | Sourced |
| 3 | Ghosting | The apps treat it as a problem: Hinge tested "Your Turn Limits" against ghosting. ([ABC News/AP](https://abcnews.go.com/Health/wireStory/feel-exhausted-swiping-dating-apps-experiencing-burnout-111507292)) No reliable statistic found. | Partly sourced |
| 4 | Fake profiles and romance scams | FTC: over 64,000 romance-scam reports and $1.14 billion lost (figure cited for 2023); about 19% of romance scams start on dating apps, 40% on social media. ([Comparitech](https://www.comparitech.com/blog/information-security/online-dating-romance-scams/), [Malwarebytes](https://www.malwarebytes.com/blog/news/2024/09/romance-scams-costlier-than-ever-10-percent-of-victims-lose-10000-or-more.)) | Sourced (secondary) |
| 5 | Pay-to-be-seen and dark patterns | FTC sued Match Group (2019) over fake "love interest" ads used to sell subscriptions and over cancellation practices; Match agreed to pay $14 million (2025). ([FTC case page](https://www.ftc.gov/enforcement/cases-proceedings/172-3013/match-group-inc)). That likes and visibility sit behind paid tiers on the big apps is widely reported but was not verified here. | Partly sourced |
| 6 | Opaque ranking ("why do I see this person?") | Users and lawsuits describe the apps as gamified; a proposed class action against Match Group alleged addictive design (Match called it meritless). ([CBC](https://www.cbc.ca/lite/story/1.7152917)) | Sourced as allegation |
| 7 | Data sharing | FTC complaint (2026) against Match Group Americas and OkCupid's operator over sharing user data contrary to its privacy policy. ([FTC](https://www.ftc.gov/system/files/ftc_gov/pdf/OkCupid-MatchComplaint.pdf)) | Sourced as allegation |
| 8 | "First move" pressure | Bumble dropped its rule that women must message first (2024) and added "Opening Moves" prompts instead. ([Fox/KTVU](https://www.ktvu.com/news/bumble-dating-app-women-first-move-opening-moves)) | Sourced |
| 9 | Looks-first judging | Bumble's 2024 redesign requires four photos ([Entrepreneur](https://www.entrepreneur.com/business-news/bumble-redesigns-app-with-updated-algorithm-first-moves/473465)); swipe apps put the photo first. That this drives superficial choices is a common complaint, not measured here. | Unverified as harm |

## What Amber does about each one

| Pain | Amber, already live (PRs #119, #128, #130) | Added in this PR |
|---|---|---|
| 1 Burnout | No swiping and no endless feed: at most 20 suggestions, each with reasons. No streaks, no "likes you" teasers. | A suggestion can now also come from **language exchange**, so a visit has a purpose beyond browsing. |
| 2 Harassment | Nobody can write until both accept the same invitation. Block and report in one step; the reported person is never told; a block cannot be detected by probing. | **Photos open only after both say yes** by default (owner's choice 2026-11-27); a person can choose to show them to every member. Photos are re-encoded on upload, which removes EXIF location data. |
| 3 Ghosting | Either person can end a conversation. | **Close kindly**: ending a conversation offers three fixed, kind goodbyes. The other person sees the sentence in "Closed kindly" for 30 days. Fixed sentences mean a goodbye can never be abusive. Each conversation shows **whose turn it is**, with a gentle nudge after 3 quiet days. |
| 4 Fakes and scams | Reports are stored for review (but nobody reviews them yet, see open decisions). | A light trust signal: **"Email confirmed"** on the card. Profiles are part of a real Deepwater account that also learns or job-hunts, which is harder to fake at scale (hypothesis, untested). |
| 5 Pay to be seen | Ranking is the same for everyone: shared intent, area, interests. | Unchanged. Proposal for the business model: never sell visibility or "who liked you"; charge, if at all, for Echo/exam features. Owner decision. |
| 6 Opaque ranking | Every suggestion lists why it appears; the order rule is written in AmberRules.java. | New reasons are just as explicit: "Speaks Danish, which you are learning", "Is learning Polish, which you speak". |
| 7 Data sharing | Amber data is never sent to an AI model. Echo data is private unless switched on. | **One profile, chosen per field**: languages you know (on by default), Echo languages with levels (off), "Uses Harbor" (off), photos (after yes). **Download my data** (JSON) and **sign out everywhere** in account settings. |
| 8 First move | Either person can invite; the invitation itself carries the shared reasons, so nobody starts from nothing. | Unchanged. |
| 9 Looks first | Cards lead with name, area, interests and reasons. | Photos are optional and, by default, come after the yes. The card still leads with what you share. |

## What is new compared with the big apps

1. **Language exchange as a matching reason.** Deepwater knows what a person learns (Echo, practice exams) and
   what they speak (Deepwater profile). Amber can say "Speaks Danish, which you are learning". The viewer's own
   learning is used only for their own view; the other person's learning counts only if they show it.
2. **One Deepwater profile.** Name others see, birth date (shown as age), city, bio, languages and photos are
   entered once and used by every product. Echo and Harbor show nothing to anyone; Amber shows the profile only
   after joining, with a per-field choice.
3. **Close kindly** instead of ghosting, with fixed sentences that cannot be used to hurt.
4. **Photos after yes** by default.

## Open owner decisions (not answered here)

- Age check: today a typed birth date (or the old typed age) plus an 18+ tick. Real age assurance? (README)
- Who reviews reports, and when Amber may launch publicly without a reviewer. (README)
- Message retention after a conversation ends. (README)
- Photo moderation: photos are not checked by anyone or any tool. With "after yes" as the default the exposure
  is smaller, but a nudity or fake-photo check is needed before a public launch. Options: a paid image-moderation
  API (cost per photo) or a human queue. Not added: it would add cost, so it needs your decision.
- Business model: confirm "never sell visibility".
