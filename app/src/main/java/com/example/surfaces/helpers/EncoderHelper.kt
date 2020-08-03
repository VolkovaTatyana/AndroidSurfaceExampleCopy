package com.example.surfaces.helpers

import android.media.*
import android.view.Surface
import com.example.surfaces.utils.LogUtils.debug
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

class EncoderHelper(
    outputFile: File,
    val encoderWidth: Int,
    val encoderHeight: Int
) {
    val surface: Surface
    private val mediaCodecVideo: MediaCodec
//    private val mediaCodecAudio: MediaCodec
//    private var audioFormat: MediaFormat
    private val bufferInfo = MediaCodec.BufferInfo()
    private val muxer: MediaMuxer

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false

    init {
        /*audioFormat = MediaFormat.createAudioFormat(AUDIO_FORMAT, SAMPLE_RATE, AUDIO_CHANNELS)
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO)
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, AUDIO_CHANNELS)*/

        val videoFormat = MediaFormat.createVideoFormat(VIDEO_FORMAT, encoderWidth, encoderHeight)
        videoFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_PER_SECOND)
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_I_FRAME_INTERVAL)

        try {
            mediaCodecVideo = MediaCodec.createEncoderByType(VIDEO_FORMAT)
//            mediaCodecAudio = MediaCodec.createEncoderByType(AUDIO_FORMAT)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        mediaCodecVideo.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
//        mediaCodecAudio.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        surface = mediaCodecVideo.createInputSurface()

        muxer = MediaMuxer(
            outputFile.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )

        mediaCodecVideo.start()
        debug("encoder start")
    }

    fun drainEncoder(endOfStream: Boolean) {
        val timeoutUs = 10000
        debug("drainEncoder($endOfStream)")

        if (endOfStream) {
            debug("sending EOS to encoder")
            mediaCodecVideo.signalEndOfInputStream()
        }

        var encoderOutputBuffers = mediaCodecVideo.outputBuffers
        loop@ while (true) {
            val encoderStatus = mediaCodecVideo.dequeueOutputBuffer(
                bufferInfo, timeoutUs.toLong()
            )

            when {
                encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) {
                        break@loop
                    } else {
                        debug("no output available, spinning to await EOS")
                    }
                }

                encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    encoderOutputBuffers = mediaCodecVideo.outputBuffers
                }

                encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) {
                        throw IllegalStateException("format changed twice")
                    }

                    val newFormat = mediaCodecVideo.outputFormat
                    debug("encoder output format changed $newFormat")

                    videoTrackIndex = muxer.addTrack(newFormat)
//                    audioTrackIndex = muxer.addTrack(audioFormat)
                    muxer.start()
                    muxerStarted = true
                }

                encoderStatus < 0 -> {
                    debug("unexpected result from dequeueOutput")
                }

                else -> {
                    val encodedData = encoderOutputBuffers[encoderStatus]
                        ?: throw IllegalStateException("encoded output buffer")

                    if (
                        (bufferInfo.flags and
                        MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    ) {
                        debug("ignoring buffer_flag_codec")
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0) {
                        if (!muxerStarted) {
                            throw IllegalStateException("muxer hasn't started")
                        }

                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(
                            bufferInfo.offset + bufferInfo.size
                        )

                        muxer.writeSampleData(
                            videoTrackIndex, encodedData, bufferInfo
                        )

                        debug("send ${bufferInfo.size}")
                    }

                    mediaCodecVideo.releaseOutputBuffer(encoderStatus, false)

                    if (
                        (bufferInfo.flags and
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    ) {
                        if (!endOfStream) {
                            debug("reached end of stream unexpectedly")
                        } else {
                            debug("end of stream reached")
                        }
                        break@loop
                    }
                }
            }
        }

    }

    fun release() {
        mediaCodecVideo.stop()
//        mediaCodecAudio.stop()
        mediaCodecVideo.release()
//        mediaCodecAudio.release()

        muxer.stop()
        muxer.release()
    }

    class AudioThread : Thread() {
        override fun run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            val min_buffer_size = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            var buffer_size: Int =
                SAMPLES_PER_FRAME * FRAMES_PER_BUFFER
            if (buffer_size < min_buffer_size)
                buffer_size = (min_buffer_size / SAMPLES_PER_FRAME + 1) * SAMPLES_PER_FRAME * 2

            var audioRecord: AudioRecord? = null
            for (source in AUDIO_SOURCES) {
                try {
                    audioRecord = AudioRecord(
                        source,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        buffer_size
                    )
                    if (audioRecord.state != AudioRecord.STATE_INITIALIZED) audioRecord = null
                } catch (e: Exception) {
                    audioRecord = null
                }
                if (audioRecord != null) break
            }
            if (audioRecord != null) {
                val buf =
                    ByteBuffer.allocateDirect(SAMPLES_PER_FRAME)
                var readBytes: Int
                audioRecord.startRecording()
                try {

                    // read audio data from internal mic
                    buf.clear()
                    readBytes = audioRecord.read(
                        buf,
                        SAMPLES_PER_FRAME
                    )
                    if (readBytes > 0) {
                        // set audio data to encoder
                        buf.position(readBytes)
                        buf.flip()}
                } finally {
                    audioRecord.stop()
                }
            }
        }
    }

    private companion object {
        const val VIDEO_FORMAT = "video/avc"
        const val VIDEO_FRAME_PER_SECOND = 30
        const val VIDEO_I_FRAME_INTERVAL = 2
        const val VIDEO_BITRATE = 3000 * 1000
        //audio
        const val AUDIO_FORMAT = "audio/mp4a-latm" //MIME_TYPE
        const val SAMPLE_RATE = 44100 // 44.1[KHz] is only setting guaranteed to be available on all devices.
        const val BIT_RATE = 64000
        const val SAMPLES_PER_FRAME = 1024 // AAC, bytes/frame/channel
        const val FRAMES_PER_BUFFER = 30 // AAC, frame/buffer/sec

        const val AUDIO_BITRATE = 48000
        const val AUDIO_CHANNELS = 1

        private val AUDIO_SOURCES = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        )
    }
}