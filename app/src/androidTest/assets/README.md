Generated, non-personal H.264/yuv420p fixtures (15 fps, 1 second). `white.mp4` and
`indoor.mp4` are 720×1280 flat white and #987f60 respectively. The third fixture
is 1280×720 with 440 Hz AAC audio and a 90° clockwise display matrix (Android rotation=90). Total size: ~18 KB.

Recreate with FFmpeg:

```sh
ffmpeg -f lavfi -i color=c=white:s=720x1280:r=15:d=1 -an -c:v libx264 -pix_fmt yuv420p -movflags +faststart white.mp4
ffmpeg -f lavfi -i color=c=0x987f60:s=720x1280:r=15:d=1 -an -c:v libx264 -pix_fmt yuv420p -movflags +faststart indoor.mp4
ffmpeg -f lavfi -i color=c=0x987f60:s=1280x720:r=15:d=1 -f lavfi -i sine=frequency=440:sample_rate=48000:duration=1 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest base.mp4
ffmpeg -display_rotation:v:0 -90 -i base.mp4 -c copy rotated-with-audio.mp4
```
