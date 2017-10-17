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
- {OK} auto-start
- {OK} observe if we still have RollbackError -- those need to be caught in the `received` loop
       because the receiver does not do that (falls through `NonFatal(_)`).
- {OK} hardware shutdown/reboot buttons
- {  } prepare some long radio recording in case we have problems getting FM reception
- {  } plan for way to listen into radio to program channels
- {  } quick level and channel check using noise

mid pri

- {  } proper db-span selection
- {  } volume control?

lo pri

- {OK} modulate space-frames?
- {OK} shrink phase again after a while? seems to get stuck at 30s

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

## match

The idea is as follows:
- we'll buffer the 'time-frequency-matrix' for the punch in and punch out, using `RepeatWindow`
 (should be unproblematic in terms of keeping those in memory)
- then we run the sliding mfcc just as in select-overwrite on the database, and link it with the 
  two repeated windows through pearson
- from overwrite-instruction and side-frames, we have
  - punch-in : start = max(0, min(phrase-length, instr.span.start) - sideFrames); stop = min(phrase-length, start + sideFrames)
  - punch-out: start = max(0, min(phrase-length, instr.span.stop )); stop = min(phrase-length, start + sideFrames)
  - from this we calculate the offset between the two taps into the db: run-length = punch-out.start - punch-in.start
  - from that we calculate the run length: db.length - sideFrames - run-length; if that's <= 0, abort here
  - from that we calculate the number of window steps (/repetitions)
  