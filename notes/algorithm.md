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
- {  } proper db-span selection
- {OK} occasional front/back trim
- {  } prepare some long radio recording in case we have problems getting FM reception
- {  } track timeout for iteration; ensure that iteration is not re-entered
- {  } rota speed-lim
- {  } withering of 'old' data (idea: maintain files parallel to phase that track age, i.e. cycles in copying;
  then this could lead to increased bleach etc., so we flush penetrating resonances)
- {  } modulate space-frames?
- {  } hardware shutdown/reboot buttons
- {  } plan for way to listen into radio to program channels
- {  } auto-start

## alsa-mixer

- value 0 is minimum; value 25 is -12 dB
