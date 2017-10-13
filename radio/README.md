# Dealing with the PITA of Gqrx

- version used is 2.6
- `xdotool` must be installed to simulate clicks and key presses in the GUI, as the 
  app doesn't remember its DSP state
- you must make sure that the `wrtng.conf` file is write protected, otherwise gqrx
  overwrites the configuration upon exit, setting the `crashed=true` flag if we
  hard-quit, and that would result in GUI interaction dialogs popping up next time.
- conf files have to go to `~/.config/gqrx/`
- recordings are always 48 kHz stereo, no way to customise, no way to get the file
  name, other than perhaps parsing the console output of the app itself.
