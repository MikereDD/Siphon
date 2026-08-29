# Siphon support

## Start here

If Siphon fails to extract from a website, first update the extractor from **Settings → Update extractor** and retry. Websites change much more frequently than the application itself.

For local-video problems, confirm Siphon can see the source video through Android's media picker/library and that storage permission has been granted where required by your Android version.

## When opening an issue

Include:

- Siphon version;
- Android version;
- device model/ABI;
- source type (**local video** or **link**);
- requested output format and quality;
- whether the extractor is stable or nightly;
- exact reproducible steps;
- sanitized logs if relevant.

Do **not** post cookies, tokens, private URLs, credentials, signing files, or other secrets.

## Link extraction vs. app bugs

A site-specific extraction failure may originate upstream in yt-dlp or in changes made by the website. A successful local-video extraction is useful evidence when distinguishing an upstream extractor issue from a Siphon pipeline problem.

## Feature requests

Feature requests are welcome when they fit Siphon's core purpose: focused, reliable audio extraction without unnecessary complexity.
