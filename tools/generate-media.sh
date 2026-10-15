#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
ffmpeg -y -v error -f lavfi -i 'testsrc2=size=720x1280:rate=30:duration=6' \
  -an -c:v libx264 -preset veryfast -crf 22 -pix_fmt yuv420p \
  -colorspace bt709 -color_primaries bt709 -color_trc bt709 -movflags +faststart \
  android/app/src/main/res/raw/sample_motion.mp4
ffmpeg -y -v error -i android/app/src/main/res/raw/sample_motion.mp4 -frames:v 1 -update 1 \
  android/app/src/main/res/drawable-nodpi/sample_cover.png
cp android/app/src/main/res/raw/sample_motion.mp4 ios/StrangerShaders/Resources/SampleMotion.mp4
cp android/app/src/main/res/drawable-nodpi/sample_cover.png \
  ios/StrangerShaders/Resources/Assets.xcassets/sample-cover.imageset/sample-cover.png
