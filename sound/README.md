Files to copy apart from installing the `.deb`:

- `QjackCtl.conf`               goes to `~/.config/rncbc.org/`
- `jack-defaults.xml`           goes to `~/Documents/`
- `wrtng-mchn-sound.desktop`    goes to `~/.config/autostart/`

dot 17 should have `--buttons` switch to run RPi buttons 29 (shutdown) and 30 (reboot).
It's that 17 is the only one that has an older kernel version which works despite the bloody WiringPi bug.
