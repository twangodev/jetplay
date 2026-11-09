Generated test fixtures (no third-party media):

24bit.flac:
  ffmpeg -f lavfi -i sine=frequency=440:sample_rate=48000:duration=0.2 -c:a flac -sample_fmt s32 24bit.flac
  Source depth is 24 bits, while decoded storage is s32.

six-channels.wav:
  ffmpeg -f lavfi -i anullsrc=channel_layout=6.0:sample_rate=8000 -t 0.05 -c:a pcm_s16le six-channels.wav
  Six channels have a 6.0 layout, not 5.1.

vfr.mp4:
  ffmpeg -f lavfi -i testsrc=size=64x64:rate=30:duration=2 -vf "select=eq(n\,0)+eq(n\,1)+eq(n\,59)" -fps_mode vfr -c:v libx264 -pix_fmt yuv420p vfr.mp4
  Three frames at 0, 0.033333, and 1.966667 seconds; container duration is 2 seconds.
