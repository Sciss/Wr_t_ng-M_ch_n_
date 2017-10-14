# algorithm

- it only makes sense if there is some coherence between the channels
- although it has overhead, the "simplest" way would be that the complete
  phrase is relayed from channel to channel
- then each node maintains their individual database (this could even be
  using different radio frequencies)

## to-do

- AGC
- relaying: order (or change cables)
- relaying: pass audio? now i think it's better without
- proper db-span selection
- occasional front/back trim
- prepare some long radio recording in case we have problems getting FM reception
- track timeout for iteration; ensure that iteration is not re-entered

## alsa-mixer

- value 0 is minimum; value 25 is -12 dB
