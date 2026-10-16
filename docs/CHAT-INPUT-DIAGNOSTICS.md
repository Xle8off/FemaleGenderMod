# Chat input investigation (historical)

Diagnostics have been removed from the final beta.199 build after identifying the
No Chat Reports EditBox-only focus restriction. The markers and reproduction procedure
below describe the temporary beta.197–198 investigation builds, not the current release.

Install the matching loader build, restart the client, connect and reproduce:

1. Open chat with T and try typing a few letters.
2. Close it, open with / and try typing.
3. Click a player name to insert /w and try typing again.
4. Save latest.log immediately. Note which steps accepted keyboard input and whether the bell was visible.

Markers:

- SP-input / keyboard-key-enter: keyboard callback reached while ChatScreen was open.
- SP-input / keyboard-character-enter: character callback reached; no character value is logged.
- SP-input / composer-character-enter/return: an EditBox or MultiLineEditBox received the character; consumed reports the result.
- SP-input / chat-key-enter/return: chat key handler reached; focus identifies its current widget.
- SP-session: interface activation changes and Fabric appearance hello protocol.
- SP-screen: screen initialization, chat controls creation, widgets clearing/rebuilding,
  focus changes with caller stack, loader post-init, bell creation and visibility changes.
  Screen identity and current-screen match distinguish delayed initialization.

The logger records no message text, character values, clipboard, key codes, player names or server addresses.
Input logging is capped at 120 events per 60-second window. An absent marker alone does not prove which mod intercepted an event.

Beta.199 restores the parent focused listener when an EditBox-only override rejects the custom
composer. Test with No Chat Reports enabled: T, /, editing after /w, switching out of command
mode, Enter, Esc, history and Tab. The SP-screen compatibility-focus-recovered marker indicates
the fallback ran. Normal accepted focus requests do not use the fallback.

The hud-bell preference now applies only outside an open chat; chat bell visibility ignores it
(an active notification popup still temporarily hides the bell).

Includes client-thread disconnect cleanup and the separate appearance_sync:chat_state transport.
The corresponding SpringPlusChat server build is required for state updates; old clients no longer receive staff/spy state via chat.
