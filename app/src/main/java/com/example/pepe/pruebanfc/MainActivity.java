package com.example.pepe.pruebanfc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.nfc.NfcAdapter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Locale;
import java.nio.charset.Charset;

import static java.util.Arrays.copyOfRange;


/**
 * Activity for reading data from an NDEF Tag.
 *
 * @author Ralf Wondratschek
 *
 */
public class MainActivity extends Activity {

    public static final String MIME_TEXT_PLAIN = "text/plain";
    public static final String MIME_PDF = "application/pdf";
    public static final String MIME_GIF = "image/gif";

    public static final String TAG = "NfcDemo";

    private TextView mTextView;
    private NfcAdapter mNfcAdapter;
    private NdefMessage mMessage;


    public static NdefRecord newTextRecord(String text, Locale locale, boolean encodeInUtf8) {
        byte[] langBytes = locale.getLanguage().getBytes(Charset.forName("US-ASCII"));

        Charset utfEncoding = encodeInUtf8 ? Charset.forName("UTF-8") : Charset.forName("UTF-16");
        byte[] textBytes = text.getBytes(utfEncoding);

        int utfBit = encodeInUtf8 ? 0 : (1 << 7);
        char status = (char) (utfBit + langBytes.length);

        byte[] data = new byte[1 + langBytes.length + textBytes.length];
        data[0] = (byte) status;
        System.arraycopy(langBytes, 0, data, 1, langBytes.length);
        System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);

        return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], data);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //mTextView = (TextView) findViewById(R.id.textView_explanation);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (mNfcAdapter == null) {
            // Stop here, we definitely need NFC
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show();
            finish();
            return;

        }

        if (!mNfcAdapter.isEnabled()) {
            //mTextView.setText("NFC is disabled.");
        } else {
            //mTextView.setText(R.string.explanation);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        // Crear un mensaje NDEF para envio en background
        mMessage = new NdefMessage(
                new NdefRecord[] { newTextRecord("NDEF Push Sample ewew", Locale.ENGLISH, true)});

        mNfcAdapter.setNdefPushMessage(mMessage, this);
        handleIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();

        /**
         * It's important, that the activity is in the foreground (resumed). Otherwise
         * an IllegalStateException is thrown.
         */
        setupForegroundDispatch(this, mNfcAdapter);
        //if (mNfcAdapter != null) mNfcAdapter.enableForegroundNdefPush(this, mMessage);
    }

    @Override
    protected void onPause() {
        /**
         * Call this before onPause, otherwise an IllegalArgumentException is thrown as well.
         */
        stopForegroundDispatch(this, mNfcAdapter);

        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        /**
         * This method gets called, when a new Intent gets associated with the current activity instance.
         * Instead of creating a new activity, onNewIntent will be called. For more information have a look
         * at the documentation.
         *
         * In our case this method gets called, when the user attaches a Tag to the device.
         */
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        // TODO: handle Intent
        TextView mText = (TextView) findViewById(R.id.mTextView);

        mText.setText("Hello here");
        //mTextView.setText("Readed content ");
        String action = intent.getAction();
        TextView mTextAction = (TextView) findViewById(R.id.textAction);
        mTextAction.setText(action);
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {

            String type = intent.getType();
            if (MIME_TEXT_PLAIN.equals(type)) {

                mText.setText("Plain Text");
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                new NdefReaderTask().execute(tag);

            } else if (MIME_GIF.equals(type)) {
                mText.setText("GIF IMAGE");
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                new NdefReaderTask().execute(tag);
            }
            else {
                mText.setText("Wrong mime type: " + type);
                Log.d(TAG, "Wrong mime type: " + type);
            }
        } else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {

            mText.setText("In Tech");
            // In case we would still use the Tech Discovered Intent
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            String[] techList = tag.getTechList();
            String searchedTech = Ndef.class.getName();

            for (String tech : techList) {
                if (searchedTech.equals(tech)) {
                    new NdefReaderTask().execute(tag);
                    break;
                }
            }
        } else if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            new NdefReaderTask().execute(tag);
        }

    }

    /**
     * @param activity The corresponding {@link Activity} requesting the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{};

        // Notice that this is the same filter as in our manifest.
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);
        try {
            filters[0].addDataType(MIME_TEXT_PLAIN);
            filters[0].addDataType(MIME_PDF);
        } catch (MalformedMimeTypeException e) {
            throw new RuntimeException("Check your mime type.");
        }

        //adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
        adapter.enableForegroundDispatch(activity, pendingIntent, null, null);
    }

    /**
     * @param activity The corresponding {@link BaseActivity} requesting to stop the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }



    /**
     * Background task for reading the data. Do not block the UI thread while reading.
     *
     * @author Ralf Wondratschek
     *
     */
    private class NdefReaderTask extends AsyncTask<Tag, Void, String> {

        byte[] message;
        @Override
        protected String doInBackground(Tag... params) {

            Tag tag = params[0];

            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {
                return "Not NDEF tag " + tag.toString();

                // NDEF is not supported by this Tag.
                //return null;
            }

            NdefMessage ndefMessage = ndef.getCachedNdefMessage();

            if (ndefMessage == null)
            {
                return "No message";
                //return null;
            }

            NdefRecord[] records = ndefMessage.getRecords();
            if (records == null)
            {
                return "No records";
                //return null;
            }
            for (NdefRecord ndefRecord : records) {
                byte[] RTD_INVOICE = {0X077};

                if (ndefRecord.getTnf() == NdefRecord.TNF_MIME_MEDIA)
                {
                    message = ndefRecord.getPayload();
                    return null;
                    //return "content mime:" + readText(ndefRecord);

                }
                if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {

                    try {
                        message = readText(ndefRecord);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
                else if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), RTD_INVOICE)) {
                    try {
                        message = readText(ndefRecord);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
                else {
                    String strType = "";
                    byte[] types = ndefRecord.getType();
                    for (byte type : types)
                    {
                        strType += type + " ,";
                    }
                    return "Record undef:" + ndefRecord.getTnf() + " Type:" + strType;
                }
            }

            return "No message";
        }

        private byte[] readText(NdefRecord record) throws UnsupportedEncodingException {
        /*
         * See NFC forum specification for "Text Record Type Definition" at 3.2.1
         *
         * http://www.nfc-forum.org/specs/
         *
         * bit_7 defines encoding
         * bit_6 reserved for future use, must be 0
         * bit_5..0 length of IANA language code
         */

            byte[] payload = record.getPayload();

            // Get the Text Encoding
            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";

            // Get the Language Code
            int languageCodeLength = payload[0] & 0x3F;

            // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
            // e.g. "en"
            byte [] byteContent = copyOfRange(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1);
            //String text =new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, "ASCII");

            return byteContent;
            // Get the Text
            //return text;
        }

        protected String processText(byte[] text)
        {


            String resultText = "<html style=' font-family: \"Courier\"; font-size: 14px'><body style='margin:2px;'>";
            boolean bold = false;
            boolean doublestrike = false;
            boolean doublewidth = false;
            int extraline = 0;

            resultText += "<div style='min-height:17px;'><div style='float:left'>";
            for (int i=0; i<text.length; i++) {
                byte ch = text[i];
                switch (ch) {
                    case 27:
                        i++;
                        byte ch2 = text[i];
                        switch (ch2)
                        {
                            case '!': {
                                i++;
                                byte ch3 = text[i];
                                if (ch3 == 0) {
                                    if (bold)
                                    {
                                        resultText += "</b>";
                                        bold = false;
                                    }

                                    if (doublestrike || doublewidth)
                                    {
                                        resultText += "</div><div  style='float:left'>";
                                        doublestrike = false;
                                        doublewidth = false;

                                    }


                                }


                                if (((int)ch3 & 32) == 32) {
                                    //resultText += "<font size=5>";
                                    //resultText += "<font size=\"6\">";
                                    doublewidth= true;
                                    extraline++;
                                }

                                if (((int)ch3 & 16) == 16) {
                                    //resultText += "</div><div style='float:left; line-height:25px; transform: scale(1,2); ms-transform: scale(1,2); -webkit-transform: scale(1,2); -moz-transform:scale(1,2); -o-transform:scale(1,2);'>";
                                    doublestrike = true;
                                    extraline++;
                                }

                                if (doublewidth || doublestrike)
                                {
                                    String scaleX = doublewidth? "2" : "1";
                                    String scaleY = doublestrike? doublewidth? "3" : "2" : "1";
                                    /*String marginBottom = "1px";
                                    if (doublestrike && doublewidth)
                                        marginBottom = "2px";*/

                                    String scale = "scale("+scaleX+","+scaleY+")";
                                    resultText += "</div><div style='float:left; min-height:30px; margin-left:-1px; transform: "+scale+"; ms-transform: "+scale+"; -webkit-transform: "+scale+"; -moz-transform:"+scale+"; -o-transform:"+scale;
                                    //resultText += "'>";
                                    resultText +="; position:relative; transform-origin: top left;-webkit-transform-origin: top left;-moz-transform-origin: top left; -o-transform-origin: top left;'>";
                                }

                                if (((int)ch3 & 8) == 8) {
                                    resultText += "<b>";
                                    bold = true;
                                }



                                break;
                            }

                            // 27, 116, 19, 213  - Simbolo del euro
                            case 't': {
                                i++;
                                byte ch3 = text[i];
                                if (ch3 == 19 && (text[i+1] & 0xff) == 213  )
                                {
                                    i++;
                                    resultText += "&euro;";
                                }

                            }


                        }
                        break;

                    case 29: // GS
                        i++;
                        ch2 = text[i];
                        switch (ch2) {
                            case '!': {
                                i++;
                                byte ch3 = text[i];
                                if (ch3 == 0) {
                                    if (bold) {
                                        resultText += "</b>";
                                        bold = false;
                                    }

                                    if (doublestrike || doublewidth) {
                                        resultText += "</div><div  style='float:left'>";
                                        doublestrike = false;
                                        doublewidth = false;

                                    }


                                }


                                if (((int) ch3 & 32) == 32) {
                                    //resultText += "<font size=5>";
                                    //resultText += "<font size=\"6\">";
                                    doublewidth = true;
                                    extraline++;
                                }

                                if (((int) ch3 & 16) == 16) {
                                    //resultText += "</div><div style='float:left; line-height:25px; transform: scale(1,2); ms-transform: scale(1,2); -webkit-transform: scale(1,2); -moz-transform:scale(1,2); -o-transform:scale(1,2);'>";
                                    doublestrike = true;
                                    extraline++;
                                }

                                if (doublewidth || doublestrike) {
                                    String scaleX = doublewidth ? "2" : "1";
                                    String scaleY = doublestrike ? doublewidth ? "3" : "2" : "1";
                                    /*String marginBottom = "1px";
                                    if (doublestrike && doublewidth)
                                        marginBottom = "2px";*/

                                    String scale = "scale(" + scaleX + "," + scaleY + ")";
                                    resultText += "</div><div style='float:left; min-height:30px; margin-left:-1px; transform: " + scale + "; ms-transform: " + scale + "; -webkit-transform: " + scale + "; -moz-transform:" + scale + "; -o-transform:" + scale;
                                    //resultText += "'>";
                                    resultText += "; position:relative; transform-origin: top left;-webkit-transform-origin: top left;-moz-transform-origin: top left; -o-transform-origin: top left;'>";
                                }

                                if (((int) ch3 & 8) == 8) {
                                    resultText += "<b>";
                                    bold = true;
                                }


                                break;
                            }
                            case 'v': {
                                i++;
                                byte ch3 = text[i];

                            }
                        }

                    // retornos de carro
                    case '\n':
                        resultText += "</div></div>";
                        for (int iExtra=0;iExtra<extraline;iExtra++)
                            resultText += "<br>";
                        resultText += "<div style='min-height:17px;'><div style='float:left'>";
                        extraline = 0;
                        break;

                    // espacios en blanco
                    case 32:
                        resultText += "&nbsp;";
                        break;

                    default:
                        resultText+=(char)ch;
                }
            }

            resultText += "</div></p><br></body></html>";
            return resultText;
        }
        @Override
        protected void onPostExecute(String result) {
            if (result == null) {


                TextView mText = (TextView) findViewById(R.id.mTextView);

                VirtualPrinter vp = new VirtualPrinter();
                vp.Initialize();
                String resultText = vp.processText(message);
                //String resultText = processText(message);
                WebView webview = (WebView) findViewById(R.id.webview);
                webview.getSettings().setJavaScriptEnabled(true);
                webview.loadDataWithBaseURL("", resultText, "text/html", "UTF-8", "");
                //mText.setText(Html.fromHtml(resultText));
            }
            else {
                TextView mText = (TextView) findViewById(R.id.mTextView);
                mText.setText("Error: " + result);

            }
        }
    }


}

