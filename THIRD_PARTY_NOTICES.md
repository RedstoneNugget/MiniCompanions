# Third-party notices

## Take Your Stream Chat (ijustseen)

- Project: "Take Your Stream Chat" (formerly "Take Your MineStream"), author **ijustseen**
- Source: https://github.com/ijustseen/take-your-minestream-template-1.21.7 · https://modrinth.com/mod/take-your-stream-chat
- Version used as reference: `main` branch, mod version 2.1.1 (multi-version Stonecutter build whose
  active/base version is Minecraft 1.21.11)

Mini Companions does **not** bundle or depend on this mod. Its Twitch chat connection
(`de.minicompanions.streamer.TwitchChatSource`, `TwitchIrcParser`, `ChatRoles`) was written for this
project following the same principle as Take Your Stream Chat's `TwitchChatClient` and
`ChatAuthorRoles`: anonymous read-only login as `justinfanNNNNN` to `irc.chat.twitch.tv:6697` (TLS),
`CAP REQ` for tags/commands, `JOIN #channel`, PING/PONG handling, and role detection from the IRC
`badges` tag. Extensions in this project: exponential reconnect backoff, read timeout, clean shutdown,
moderation events (CLEARCHAT/CLEARMSG), tag unescaping and strict input validation.
Everything not needed (3D message cards, HUD, pins, emotes, other platforms) was left out.

License note: the project README states the MIT license, while the repository's `LICENSE` file
contains CC0 1.0 Universal. Attribution is given here in either case; the MIT notice for the
referenced work reads:

```
Copyright (c) ijustseen

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.
```

## Kick, YouTube und TikTok (Protokoll-Informationen)

Die Anbindungen an Kick, YouTube und TikTok sind eigenständig in Java geschrieben. Es wurde kein Code
anderer Projekte übernommen. Verwendet wurden nur technische Fakten über die öffentlichen Schnittstellen
der Plattformen, die zur Interoperabilität nötig sind: Adressen, Parameter- und Ereignisnamen sowie
Protobuf-Feldnummern. Recherchiert wurden sie unter anderem in diesen Projekten:

- TikTok-Live-Connector von zerodytrash (https://github.com/zerodytrash/TikTok-Live-Connector, AGPL-3.0;
  ältere Versionen MIT) und das npm-Paket `tiktok-live-proto` (AGPL-3.0): TikTok-LIVE-Ablauf und
  Nachrichtenformate.
- Euler Stream (https://www.eulerstream.com): Signierdienst für TikTok-LIVE-Verbindungen. Die Mod
  nutzt ihn zur Laufzeit als externen Dienst.
- Öffentlich dokumentierte Beobachtungen der Community zu Kicks Pusher-Chat und zum YouTube-Livechat.

Kick, YouTube und TikTok sind Marken ihrer jeweiligen Inhaber. Die Mod ist kein offizielles Produkt
dieser Plattformen.

## Minecraft / Fabric

Minecraft is a trademark of Mojang/Microsoft. Fabric Loader and Fabric API are licensed under
Apache-2.0. This mod uses only their public APIs and does not redistribute them.
