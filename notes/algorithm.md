# algorithm

- it only makes sense if there is some coherence between the channels
- although it has overhead, the "simplest" way would be that the complete
  phrase is relayed from channel to channel
- then each node maintains their individual database (this could even be
  using different radio frequencies)

## to-do

- {OK} AGC
- {OK} relaying: order (or change cables)
- {no} relaying: pass audio? now i think it's better without
- {OK} occasional front/back trim
- {OK} withering of 'old' data (idea: maintain files parallel to phase that track age, i.e. cycles in copying;
  then this could lead to increased bleach etc., so we flush penetrating resonances)

hi pri

- {OK} track timeout for iteration; ensure that iteration is not re-entered
- {OK} rota speed-lim -- now we need the opposite; we need to rotate earlier, because the process
       takes longer to calculate; we could rotate earliest after radio-acquire has completed!
- {  } prepare some long radio recording in case we have problems getting FM reception
- {  } hardware shutdown/reboot buttons; volume control?
- {  } plan for way to listen into radio to program channels
- {  } auto-start
- {  } quick level and channel check using noise
- {  } observe if we still have RollbackError -- those need to be caught in the `received` loop
       because the receiver doesn't do that (falls through `NonFatal(_)`).

mid pri

- {  } proper db-span selection

lo pri

- {  } modulate space-frames?

## alsa-mixer

- value 0 is minimum; value 25 is -12 dB

## withering

- in a sample phase, we find `keys` max value to be 0.00112
- so in order to trump that, the withering code must reach around 0.0012 (or 1/800) after
  the desired number of iterations
- i think it gets annoying after around 20 to 40 iterations
- we need headroom to accommodate for unprecedented discontinuity levels (say, 0.002 or 1/500).
- so if, e.g., 30 iterations produces the equivalent of 0.0012, we should clip at 100 iterations
  with the nominal control file level of 0 dBFS. then we could simply add 0.01 add each copy operation
  (and clip to 1.0). in the comparision, we scale down by 0.0012 / (0.01 * 30) = 0.004
