# Emoji catalog

Unicode 17: 3,944 fully-qualified sequences, plus 1,272 alternate qualification
forms for decoding existing messages. Nine categories, Russian CLDR names and
keywords, English-name search. The picker uses 18px icons on a 24px grid,
up to fourteen columns and eight rows, mouse-wheel paging, search and category icons.

Messages sent to the server and stored in history remain original Unicode.
Only display components substitute composite sequences with private-use font glyphs.
EditBox formatting preserves original UTF-16 text, so cursor and selection offsets
remain valid. Longest-match decoding treats flags, skin tones and ZWJ sequences
as single display glyphs. The twelve old aliases/sticker tokens remain compatible.

The complete font is bundled for offline history rendering (sixteen 576px atlases,
about 4.7MB PNG data), rather than downloading thousands of independent textures.
Samson and other sticker packs remain web-hosted; this change does not embed them.

Reproduce with scripts/build-emoji-catalog.mjs, supplying the Twemoji v17.0.3
assets/72x72 directory, Unicode 17 emoji-test.txt, and output resource directories.
Place CLDR Russian annotations as ru.json and ru-derived.json beside emoji-test.txt.
The generator uses the existing website's pngjs dependency; artwork attribution
and Unicode data license are included in the resource roots.

Both loader builds run ChatReactionsCheck, checking all 3,944 sequences and
1,272 variants, cursor preservation, atlas mappings, categories and Russian search.
In-game visual verification is still required.
