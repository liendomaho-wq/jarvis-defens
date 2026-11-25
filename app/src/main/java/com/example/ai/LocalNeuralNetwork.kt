package com.example.ai

import com.example.model.JarvisIntentType
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

data class NeuralPrediction(
    val intent: JarvisIntentType,
    val confidence: Float,
    val secondIntent: JarvisIntentType,
    val secondConfidence: Float
)

/**
 * Pure-Kotlin Multi-Layer Perceptron (MLP) Neural Network built from scratch.
 * Architecture: Input(256) -> Dense(96, LeakyReLU) -> Dense(48, LeakyReLU) -> Dense(C, Softmax).
 * Trained via backpropagation with Adam optimizer + L2 weight decay, supporting live
 * incremental on-device fine-tuning and binary weight persistence.
 */
class LocalNeuralNetwork(
    private val weightsFile: File? = null
) {
    companion object {
        private const val MODEL_MAGIC = 0x4A525633 // "JRV3"
        private const val INPUT_DIM = TextVectorizer.VECTOR_DIM
        private const val HIDDEN1_DIM = 96
        private const val HIDDEN2_DIM = 48
        private const val LEAKY_SLOPE = 0.05f
        private const val ADAM_BETA1 = 0.9f
        private const val ADAM_BETA2 = 0.999f
        private const val ADAM_EPS = 1e-8f
        private const val WEIGHT_DECAY = 1e-4f
    }

    private val classes = JarvisIntentType.entries.toTypedArray()
    private val numClasses = classes.size

    // Layer 1: [HIDDEN1_DIM][INPUT_DIM]
    private var w1 = Array(HIDDEN1_DIM) { FloatArray(INPUT_DIM) }
    private var b1 = FloatArray(HIDDEN1_DIM)
    private var mw1 = Array(HIDDEN1_DIM) { FloatArray(INPUT_DIM) }
    private var vw1 = Array(HIDDEN1_DIM) { FloatArray(INPUT_DIM) }
    private var mb1 = FloatArray(HIDDEN1_DIM)
    private var vb1 = FloatArray(HIDDEN1_DIM)

    // Layer 2: [HIDDEN2_DIM][HIDDEN1_DIM]
    private var w2 = Array(HIDDEN2_DIM) { FloatArray(HIDDEN1_DIM) }
    private var b2 = FloatArray(HIDDEN2_DIM)
    private var mw2 = Array(HIDDEN2_DIM) { FloatArray(HIDDEN1_DIM) }
    private var vw2 = Array(HIDDEN2_DIM) { FloatArray(HIDDEN1_DIM) }
    private var mb2 = FloatArray(HIDDEN2_DIM)
    private var vb2 = FloatArray(HIDDEN2_DIM)

    // Output layer: [numClasses][HIDDEN2_DIM]
    private var w3 = Array(numClasses) { FloatArray(HIDDEN2_DIM) }
    private var b3 = FloatArray(numClasses)
    private var mw3 = Array(numClasses) { FloatArray(HIDDEN2_DIM) }
    private var vw3 = Array(numClasses) { FloatArray(HIDDEN2_DIM) }
    private var mb3 = FloatArray(numClasses)
    private var vb3 = FloatArray(numClasses)

    private var adamStep = 0

    @Volatile
    var isReady: Boolean = false
        private set

    @Volatile
    var lastTrainingAccuracy: Float = 0f
        private set

    @Synchronized
    fun initializeOrTrain(extraSamples: List<Pair<String, JarvisIntentType>> = emptyList()) {
        if (weightsFile != null && loadWeights(weightsFile)) {
            if (extraSamples.isNotEmpty()) {
                fineTunePairs(extraSamples, epochs = 10, learningRate = 0.005f)
                saveWeights(weightsFile)
            }
            isReady = true
            return
        }

        initHeWeights()
        resetAdamState()
        val dataset = (JarvisTrainingCorpus.samples + extraSamples).map { (text, intent) ->
            TextVectorizer.vectorize(text) to intent.ordinal
        }
        trainEpochs(dataset, epochs = 60, learningRate = 0.008f)
        lastTrainingAccuracy = evaluateAccuracy(dataset)
        if (weightsFile != null) {
            saveWeights(weightsFile)
        }
        isReady = true
    }

    @Synchronized
    fun fineTune(phrase: String, targetIntent: JarvisIntentType, epochs: Int = 18) {
        if (!isReady) initializeOrTrain()
        val vec = TextVectorizer.vectorize(phrase)
        val single = listOf(vec to targetIntent.ordinal)
        trainEpochs(single, epochs = epochs, learningRate = 0.007f)
        weightsFile?.let { saveWeights(it) }
    }

    @Synchronized
    fun fineTunePairs(samples: List<Pair<String, JarvisIntentType>>, epochs: Int = 12, learningRate: Float = 0.006f) {
        if (samples.isEmpty()) return
        val dataset = samples.map { (text, intent) -> TextVectorizer.vectorize(text) to intent.ordinal }
        trainEpochs(dataset, epochs = epochs, learningRate = learningRate)
        weightsFile?.let { saveWeights(it) }
    }

    @Synchronized
    fun predict(rawText: String): NeuralPrediction {
        if (!isReady) {
            initializeOrTrain()
        }
        val x = TextVectorizer.vectorize(rawText)
        val (_, _, probs) = forward(x)

        var bestIdx = 0
        var bestProb = -1f
        var secondIdx = 0
        var secondProb = -1f
        for (i in probs.indices) {
            val p = probs[i]
            if (p > bestProb) {
                secondProb = bestProb
                secondIdx = bestIdx
                bestProb = p
                bestIdx = i
            } else if (p > secondProb) {
                secondProb = p
                secondIdx = i
            }
        }
        return NeuralPrediction(
            intent = classes[bestIdx],
            confidence = bestProb.coerceIn(0f, 1f),
            secondIntent = classes[secondIdx],
            secondConfidence = secondProb.coerceIn(0f, 1f)
        )
    }

    private fun initHeWeights() {
        val rng = Random(20261125)
        fun fillHe(matrix: Array<FloatArray>, fanIn: Int) {
            val std = sqrt(2.0 / fanIn.toDouble()).toFloat()
            for (r in matrix.indices) {
                val row = matrix[r]
                for (c in row.indices) {
                    val u = (rng.nextFloat() + rng.nextFloat() + rng.nextFloat() - 1.5f) * 1.15f
                    row[c] = u * std
                }
            }
        }
        fillHe(w1, INPUT_DIM)
        fillHe(w2, HIDDEN1_DIM)
        fillHe(w3, HIDDEN2_DIM)
        b1.fill(0f)
        b2.fill(0f)
        b3.fill(0f)
    }

    private fun resetAdamState() {
        adamStep = 0
        for (r in mw1) r.fill(0f); for (r in vw1) r.fill(0f)
        for (r in mw2) r.fill(0f); for (r in vw2) r.fill(0f)
        for (r in mw3) r.fill(0f); for (r in vw3) r.fill(0f)
        mb1.fill(0f); vb1.fill(0f)
        mb2.fill(0f); vb2.fill(0f)
        mb3.fill(0f); vb3.fill(0f)
    }

    private fun trainEpochs(
        dataset: List<Pair<FloatArray, Int>>,
        epochs: Int,
        learningRate: Float
    ) {
        val rng = Random(42)
        for (epoch in 0 until epochs) {
            val lr = learningRate * (1f - (epoch.toFloat() / (epochs * 1.6f)))
            val shuffled = dataset.shuffled(rng)
            for ((x, targetIdx) in shuffled) {
                backpropAdamStep(x, targetIdx, lr)
            }
        }
    }

    private fun forward(x: FloatArray): Triple<FloatArray, FloatArray, FloatArray> {
        val a1 = FloatArray(HIDDEN1_DIM)
        for (i in 0 until HIDDEN1_DIM) {
            var sum = b1[i]
            val row = w1[i]
            for (j in 0 until INPUT_DIM) {
                val xv = x[j]
                if (xv != 0f) sum += row[j] * xv
            }
            a1[i] = if (sum > 0f) sum else sum * LEAKY_SLOPE
        }

        val a2 = FloatArray(HIDDEN2_DIM)
        for (i in 0 until HIDDEN2_DIM) {
            var sum = b2[i]
            val row = w2[i]
            for (j in 0 until HIDDEN1_DIM) {
                sum += row[j] * a1[j]
            }
            a2[i] = if (sum > 0f) sum else sum * LEAKY_SLOPE
        }

        val logits = FloatArray(numClasses)
        var maxLogit = Float.NEGATIVE_INFINITY
        for (i in 0 until numClasses) {
            var sum = b3[i]
            val row = w3[i]
            for (j in 0 until HIDDEN2_DIM) {
                sum += row[j] * a2[j]
            }
            logits[i] = sum
            if (sum > maxLogit) maxLogit = sum
        }

        var sumExp = 0f
        val probs = FloatArray(numClasses)
        for (i in 0 until numClasses) {
            val e = exp((logits[i] - maxLogit).toDouble()).toFloat()
            probs[i] = e
            sumExp += e
        }
        val invSum = 1f / sumExp.coerceAtLeast(1e-8f)
        for (i in 0 until numClasses) {
            probs[i] *= invSum
        }

        return Triple(a1, a2, probs)
    }

    private fun backpropAdamStep(x: FloatArray, targetIdx: Int, lr: Float) {
        val (a1, a2, probs) = forward(x)
        adamStep++
        val b1Corr = (1.0 - ADAM_BETA1.toDouble().pow(adamStep)).toFloat().coerceAtLeast(1e-6f)
        val b2Corr = (1.0 - ADAM_BETA2.toDouble().pow(adamStep)).toFloat().coerceAtLeast(1e-6f)
        val stepScale = lr * sqrt(b2Corr) / b1Corr

        // Output layer gradient: dZ3 = probs - oneHot(target)
        val dz3 = FloatArray(numClasses) { i ->
            probs[i] - if (i == targetIdx) 1f else 0f
        }

        // Backprop to hidden layer 2 + update W3/b3
        val da2 = FloatArray(HIDDEN2_DIM)
        for (i in 0 until numClasses) {
            val grad = dz3[i]
            val row = w3[i]
            val mRow = mw3[i]
            val vRow = vw3[i]
            for (j in 0 until HIDDEN2_DIM) {
                da2[j] += row[j] * grad
                val g = grad * a2[j] + WEIGHT_DECAY * row[j]
                val m = ADAM_BETA1 * mRow[j] + (1f - ADAM_BETA1) * g
                val v = ADAM_BETA2 * vRow[j] + (1f - ADAM_BETA2) * g * g
                mRow[j] = m
                vRow[j] = v
                row[j] -= stepScale * m / (sqrt(v) + ADAM_EPS)
            }
            val mb = ADAM_BETA1 * mb3[i] + (1f - ADAM_BETA1) * grad
            val vb = ADAM_BETA2 * vb3[i] + (1f - ADAM_BETA2) * grad * grad
            mb3[i] = mb
            vb3[i] = vb
            b3[i] -= stepScale * mb / (sqrt(vb) + ADAM_EPS)
        }

        val dz2 = FloatArray(HIDDEN2_DIM) { j ->
            da2[j] * if (a2[j] > 0f) 1f else LEAKY_SLOPE
        }

        // Backprop to hidden layer 1 + update W2/b2
        val da1 = FloatArray(HIDDEN1_DIM)
        for (i in 0 until HIDDEN2_DIM) {
            val grad = dz2[i]
            val row = w2[i]
            val mRow = mw2[i]
            val vRow = vw2[i]
            for (j in 0 until HIDDEN1_DIM) {
                da1[j] += row[j] * grad
                val g = grad * a1[j] + WEIGHT_DECAY * row[j]
                val m = ADAM_BETA1 * mRow[j] + (1f - ADAM_BETA1) * g
                val v = ADAM_BETA2 * vRow[j] + (1f - ADAM_BETA2) * g * g
                mRow[j] = m
                vRow[j] = v
                row[j] -= stepScale * m / (sqrt(v) + ADAM_EPS)
            }
            val mb = ADAM_BETA1 * mb2[i] + (1f - ADAM_BETA1) * grad
            val vb = ADAM_BETA2 * vb2[i] + (1f - ADAM_BETA2) * grad * grad
            mb2[i] = mb
            vb2[i] = vb
            b2[i] -= stepScale * mb / (sqrt(vb) + ADAM_EPS)
        }

        val dz1 = FloatArray(HIDDEN1_DIM) { j ->
            da1[j] * if (a1[j] > 0f) 1f else LEAKY_SLOPE
        }

        // Update W1/b1
        for (i in 0 until HIDDEN1_DIM) {
            val grad = dz1[i]
            val row = w1[i]
            val mRow = mw1[i]
            val vRow = vw1[i]
            for (j in 0 until INPUT_DIM) {
                val xv = x[j]
                if (xv != 0f || mRow[j] != 0f) {
                    val g = grad * xv + WEIGHT_DECAY * row[j]
                    val m = ADAM_BETA1 * mRow[j] + (1f - ADAM_BETA1) * g
                    val v = ADAM_BETA2 * vRow[j] + (1f - ADAM_BETA2) * g * g
                    mRow[j] = m
                    vRow[j] = v
                    row[j] -= stepScale * m / (sqrt(v) + ADAM_EPS)
                }
            }
            val mb = ADAM_BETA1 * mb1[i] + (1f - ADAM_BETA1) * grad
            val vb = ADAM_BETA2 * vb1[i] + (1f - ADAM_BETA2) * grad * grad
            mb1[i] = mb
            vb1[i] = vb
            b1[i] -= stepScale * mb / (sqrt(vb) + ADAM_EPS)
        }
    }

    private fun evaluateAccuracy(dataset: List<Pair<FloatArray, Int>>): Float {
        if (dataset.isEmpty()) return 0f
        var correct = 0
        for ((x, targetIdx) in dataset) {
            val (_, _, probs) = forward(x)
            var bestIdx = 0
            for (i in 1 until probs.size) {
                if (probs[i] > probs[bestIdx]) bestIdx = i
            }
            if (bestIdx == targetIdx) correct++
        }
        return correct.toFloat() / dataset.size.toFloat()
    }

    private fun saveWeights(file: File) {
        runCatching {
            file.parentFile?.mkdirs()
            DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
                out.writeInt(MODEL_MAGIC)
                out.writeInt(INPUT_DIM)
                out.writeInt(HIDDEN1_DIM)
                out.writeInt(HIDDEN2_DIM)
                out.writeInt(numClasses)
                out.writeFloat(lastTrainingAccuracy)

                fun writeMatrix(m: Array<FloatArray>) {
                    for (row in m) for (v in row) out.writeFloat(v)
                }
                fun writeVec(v: FloatArray) {
                    for (x in v) out.writeFloat(x)
                }

                writeMatrix(w1); writeVec(b1)
                writeMatrix(w2); writeVec(b2)
                writeMatrix(w3); writeVec(b3)
            }
        }
    }

    private fun loadWeights(file: File): Boolean {
        if (!file.exists()) return false
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { inp ->
                if (inp.readInt() != MODEL_MAGIC) return false
                if (inp.readInt() != INPUT_DIM) return false
                if (inp.readInt() != HIDDEN1_DIM) return false
                if (inp.readInt() != HIDDEN2_DIM) return false
                if (inp.readInt() != numClasses) return false
                lastTrainingAccuracy = inp.readFloat()

                fun readMatrix(m: Array<FloatArray>) {
                    for (row in m) for (i in row.indices) row[i] = inp.readFloat()
                }
                fun readVec(v: FloatArray) {
                    for (i in v.indices) v[i] = inp.readFloat()
                }

                readMatrix(w1); readVec(b1)
                readMatrix(w2); readVec(b2)
                readMatrix(w3); readVec(b3)
                resetAdamState()
                true
            }
        }.getOrDefault(false)
    }
}
