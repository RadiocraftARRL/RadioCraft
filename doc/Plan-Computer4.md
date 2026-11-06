# Future computer batch: the LAN (maintainer, 2026-11-06)

This comes after `doc/Plan-Computer3.md`.

Radio computers can be **networked together over data wires and/or microwave links**. That uses the existing data wire, and the microwave dish/horn links used elsewhere in the mod. All computers reachable that way form one LAN.

## Programs
- **LAN RELAY**: a chat room for the computers on the same LAN.
  - Users set a username, per player, remembered on that computer.
  - It shows who is online on the LAN.
- **LAN EMAIL**: send email to other users or computers on the LAN.
  - Addresses are `user@COMPUTERNAME`, using the computer name from SETUP.
  - Each computer keeps an inbox on its disk.
  - Mail waits for a computer that is unpowered or unreachable and is delivered when it comes back.
- **LAN REDSTONE**: trigger redstone outputs on other computers.
  - It works through a new **Computer Redstone block**, placed next to or under a computer. Up to **10 per computer**.
  - Each block is given a name in the computer.
  - Any computer on the LAN can trigger one by name: pulse, on or off. It could also read a block's input state.

## Open questions for later
- Are the programs built in or floppies? If floppies, they need recipes.
- What does the Computer Redstone block's recipe look like?
- Do microwave LAN links need line of sight, and do they have a range limit, like the existing microwave links?
- Does an op or the owner control who may trigger a computer's redstone blocks? For example, an allow list, or "LAN only" vs "anyone".
- Should LAN RELAY history be kept, and how much?
