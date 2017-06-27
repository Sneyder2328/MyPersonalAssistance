package com.sneyder.mypersonalassistance

import android.Manifest
import android.app.AlertDialog
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_main.*
import android.speech.RecognizerIntent
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.speech.tts.TextToSpeech
import android.support.annotation.StringRes
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.text.format.DateUtils
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import browse
import debug
import enterReveal
import error
import exitReveal
import inflate
import invisible
import showValues
import toast
import java.util.*

class MainActivity : AppCompatActivity(), RecognitionListener {

    private val RECORD_AUDIO_CODE = 1
    private val LANGUAGE = "language"
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var recognizerIntent: Intent? = null
    private var listening: Boolean = false
    private var mLanguage: Language = Language.ENGLISH_US
    val rotateAnimation: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.rotate) }

    enum class Language(val locale: Locale, val language: String){
        ENGLISH_US(Locale.US, "en"),
        SPANISH(Locale("es"), "es")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(intent.getStringExtra(LANGUAGE) == Language.SPANISH.language){
            mLanguage = Language.SPANISH
        }
        val config = Configuration()
        config.locale = mLanguage.locale
        baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)

        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(this)
        setUpRecognizerIntent()

        mFabVoice.setOnClickListener {_ ->
            if (!listening) ifHasRecordAudioPermission { startListening() }
            else stopListening()
        }

        textToSpeech = TextToSpeech(applicationContext, TextToSpeech.OnInitListener { status ->
            if(status == TextToSpeech.SUCCESS){
                val result: Int = textToSpeech?.setLanguage(mLanguage.locale) ?: TextToSpeech.LANG_MISSING_DATA
                if(result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED){
                    toast(R.string.language_no_supported)
                    textToSpeech = null
                }
                else{
                    speak(R.string.welcome)
                }
            }
        })
    }

    /**
     * calls textToSpeech.speak(getString(text), TextToSpeech.QUEUE_FLUSH, null)
     */
    private fun speak(@StringRes text: Int){
        speak(getString(text))
    }

    /**
     * calls textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null)
     */
    private fun speak(text: String){
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
    }

    /**
     * initialize recognizer intent
     */
    private fun setUpRecognizerIntent() {
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizerIntent?.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.packageName)
        recognizerIntent?.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, mLanguage.language)
        recognizerIntent?.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
        recognizerIntent?.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }


    /**
     * checks if Record Audio Permission is available and else request Audio Permission
     */
    fun ifHasRecordAudioPermission(func: () -> (Unit)){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED){
            func()
        }
        else requestAudioPermission()
    }

    /**
     * request Record Audio Permission with RECORD_AUDIO_CODE
     */
    fun requestAudioPermission(){
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_CODE)
    }

    /**
     * checks the permission result
     * if Record Audio Permission is granted calls function startListening()
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode){
            RECORD_AUDIO_CODE -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    startListening()
                }
                else{
                    speak(R.string.record_audio_permission_denied)
                }
            }
        }
    }

    /**
     * set listening to true
     * calls speechRecognizer.startListening(recognizerIntent)
     */
    private fun startListening() {
        listening = true
        mFabVoice.exitReveal{
            mFabVoice.invisible()
            mCircleLoading.enterReveal()
            mCircleLoading.startAnimation(rotateAnimation)
        }
        speechRecognizer?.startListening(recognizerIntent)
    }

    /**
     * calls speechRecognizer.stopListening()
     */
    private fun stopListening(){
        speechRecognizer?.stopListening()
    }


    // interface RecognitionListener functions
    override fun onReadyForSpeech(arg0: Bundle) {
        debug("onReadyForSpeech")
        mCircleLoading.setColorFilter(resources.getColor(R.color.colorAccent))
    }
    override fun onBeginningOfSpeech() {
        debug("onBeginningOfSpeech")
    }
    override fun onRmsChanged(rmsdB: Float) {
        debug("onRmsChanged: $rmsdB")
    }
    override fun onBufferReceived(buffer: ByteArray) {
        debug("onBufferReceived $buffer")
    }
    override fun onEndOfSpeech() {
        debug("onEndOfSpeech")
        mCircleLoading.setColorFilter(resources.getColor(R.color.black))
    }
    override fun onError(errorCode: Int) {
        listening = false
        mFabVoice.isEnabled = true
        mCircleLoading.setColorFilter(resources.getColor(R.color.black))
        mCircleLoading.clearAnimation()
        mCircleLoading.exitReveal{
            mCircleLoading.invisible()
            mFabVoice.enterReveal()
        }
        val errorMessage = getSpeechRecognizerError(errorCode)
        error("onError $errorMessage")
    }
    override fun onResults(results: Bundle) {
        debug("onResults")
        mCircleLoading.clearAnimation()
        mCircleLoading.exitReveal{
            mCircleLoading.invisible()
            mFabVoice.enterReveal()
        }
        listening = false
        mFabVoice.isEnabled = true
        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if(matches != null && !matches.isEmpty()){
            matches.showValues()
            executeCommand(matches[0].toLowerCase())
        }
        else{
            speak(R.string.any_match)
        }
    }
    override fun onPartialResults(arg0: Bundle) {
        debug("onPartialResults")
    }
    override fun onEvent(arg0: Int, arg1: Bundle) {
        debug("onEvent")
    }

    /**
     * finds out the error with speechRecognizer
     */
    fun getSpeechRecognizerError(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "error from server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speechRecognizer input"
            else -> "Didn't understand, please try again."
        }
    }


    fun executeCommand(command: String){
        debug("command $command")
        when {
            command.isOpenBrowser() -> {
                speak(R.string.opening_browser)
                browse("google.com")
            }
            command.isOpenUrl() -> {
                val url = command.split(" ").last()
                if(browse(url)) speak(R.string.opening_browser)
            }
            command.isWhatTime() -> {
                tellTheTime()
            }
            command.isWhatDate() -> {
                tellTheDate()
            }
            command.isChangeToEnglish() -> {
                changeLanguage(Language.ENGLISH_US.language)
            }
            command.isChangeToSpanish() -> {
                changeLanguage(Language.SPANISH.language)
            }
            else -> speak(R.string.any_match)
        }
    }

    val openCommands = arrayOf("open", "start", "abrir", "iniciar")
    val browserCommands = arrayOf("browser", "navigator", "navegador")
    val goCommands = arrayOf("go", "ir")
    val domains = arrayOf(".com", ".net", ".org")
    val language = arrayOf("language", "idioma", "lenguaje")
    val spanish = arrayOf("spanish", "español")
    val english = arrayOf("english", "ingles")

    fun String.isOpenBrowser(): Boolean{
        return (containsOneOfThese(*openCommands) && containsOneOfThese(*browserCommands))
    }

    fun String.isOpenUrl(): Boolean{
        return ((containsOneOfThese(*openCommands) || containsOneOfThese(*goCommands)) && containsOneOfThese(*domains))
    }

    fun String.isWhatTime(): Boolean{
        return containsOneOfThese("time", "hora")
    }

    fun String.isWhatDate(): Boolean{
        return containsOneOfThese("date", "fecha")
    }

    fun String.isChangeToEnglish(): Boolean{
        return containsOneOfThese(*language) && containsOneOfThese(*english)
    }

    fun String.isChangeToSpanish(): Boolean{
        return containsOneOfThese(*language) && containsOneOfThese(*spanish)
    }

    fun String.containsOneOfThese(vararg options: String): Boolean{
        options.forEach {
            if(this.contains(it)) return true
        }
        return false
    }

    private fun tellTheTime() {
        val now = Date()
        val time = DateUtils.formatDateTime(this, now.time, DateUtils.FORMAT_SHOW_TIME)
        speak(String.format(getString(R.string.time), time))
    }

    private fun tellTheDate() {
        val now = Date()
        val date = DateUtils.formatDateTime(this, now.time, DateUtils.FORMAT_SHOW_DATE)
        speak(String.format(getString(R.string.date), date))
    }

    private fun changeLanguage(language: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra(LANGUAGE, language)
        startActivity(intent)
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> {
                val view: View = inflate(R.layout.dialog_settings)
                val seekBarPitch = view.findViewById(R.id.seekBarPitch) as SeekBar
                val seekBarRate = view.findViewById(R.id.seekBarRate) as SeekBar

                seekBarPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        debug("pitch progress $progress")
                        textToSpeech?.setPitch(progress.div(10).toFloat())
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?){}
                    override fun onStopTrackingTouch(seekBar: SeekBar?){}
                })

                seekBarRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        debug("rate progress $progress")
                        textToSpeech?.setSpeechRate(progress.div(10).toFloat())
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?){}
                    override fun onStopTrackingTouch(seekBar: SeekBar?){}
                })
                val dialog = AlertDialog.Builder(this)
                dialog.setView(view)
                dialog.show()
            }
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        debug("onDestroy")
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}