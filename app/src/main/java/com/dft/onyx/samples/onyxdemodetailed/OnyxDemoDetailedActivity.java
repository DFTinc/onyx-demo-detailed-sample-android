
package com.dft.onyx.samples.onyxdemodetailed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;
import android.widget.Toast;

import com.dft.onyx.FingerprintTemplate;
import com.dft.onyx.core;
import com.dft.onyxcamera.licensing.License;
import com.dft.onyxcamera.licensing.LicenseException;
import com.dft.onyxcamera.ui.CaptureConfiguration;
import com.dft.onyxcamera.ui.CaptureConfigurationBuilder;
import com.dft.onyxcamera.ui.CaptureMetrics;
import com.dft.onyxcamera.ui.OnyxFragment;
import com.dft.onyxcamera.ui.CaptureConfiguration.Flip;
import com.dft.onyxcamera.ui.OnyxFragment.FingerprintTemplateCallback;
import com.dft.onyxcamera.ui.OnyxFragment.ProcessedBitmapCallback;
import com.dft.onyxcamera.ui.OnyxFragment.WsqCallback;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;

public class OnyxDemoDetailedActivity extends Activity {
    private final static String TAG = OnyxDemoDetailedActivity.class.getSimpleName();
    private final static String ENROLL_FILENAME = "enrolled_template.bin";

    private ImageView mFingerprintView;
    private Animation mFadeIn;
    private Animation mFadeOut;
    private OnyxFragment mFragment;
    private FingerprintTemplate mCurrentTemplate = null;
    private double mCurrentFocusQuality = 0.0;
    private FingerprintTemplate mEnrolledTemplate = null;

    static {
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Unable to load OpenCV!");
        } else {
            Log.i(TAG, "OpenCV loaded successfully");
            core.initOnyx();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupViews();
    }

    private void setupViews() {
        setContentView(R.layout.base_layout);
        mFragment = (OnyxFragment) getFragmentManager().findFragmentById(R.id.onyx_frag);
        CaptureConfiguration captureConfig = new CaptureConfigurationBuilder()
                .setProcessedBitmapCallback(mProcessedCallback)
                .setWsqCallback(mWsqCallback)
                .setFingerprintTemplateCallback(mTemplateCallback)
                .setShouldInvert(true)
                .setFlip(Flip.VERTICAL)
                .buildCaptureConfiguration();
        mFragment.setCaptureConfiguration(captureConfig);
        mFragment.setErrorCallback(mErrorCallback);
        mFragment.startOneShotAutoCapture();

        createFadeInAnimation();
        createFadeOutAnimation();

        LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        mFingerprintView = new ImageView(this);
        addContentView(mFingerprintView, layoutParams);
    }

    private void createFadeInAnimation() {
        mFadeIn = new AlphaAnimation(0.0f, 1.0f);
        mFadeIn.setDuration(500);
        mFadeIn.setAnimationListener(new AnimationListener() {

            @Override
            public void onAnimationEnd(Animation animation) {
                new CountDownTimer(1000, 1000) {

                    @Override
                    public void onFinish() {
                        mFingerprintView.startAnimation(mFadeOut);
                    }

                    @Override
                    public void onTick(long millisUntilFinished) {
                    }

                }.start();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationStart(Animation animation) {
                mFingerprintView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void createFadeOutAnimation() {
        mFadeOut = new AlphaAnimation(1.0f, 0.0f);
        mFadeOut.setDuration(500);
        mFadeOut.setAnimationListener(new AnimationListener() {

            @Override
            public void onAnimationEnd(Animation animation) {
                mFingerprintView.setVisibility(View.INVISIBLE);
                if (mEnrolledTemplate == null) {
                    createEnrollQuestionDialog();
                } else {
                    mFragment.startOneShotAutoCapture();
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationStart(Animation animation) {
            }
        });
    }

    private void createEnrollQuestionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.enroll_title);
        String enrollQuestion = getResources().getString(R.string.enroll_question);
        builder.setMessage(enrollQuestion + "\n\n" +
                "(Quality is " + (int) mCurrentFocusQuality + ")");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                enrollCurrentTemplate();
                dialog.dismiss();
                mFragment.startOneShotAutoCapture();
            }
        });

        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                mFragment.startOneShotAutoCapture();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void enrollCurrentTemplate() {
        mEnrolledTemplate = mCurrentTemplate;

        deleteEnrolledTemplateIfExists();

        try {
            FileOutputStream enrollStream = this.openFileOutput(ENROLL_FILENAME, MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(enrollStream);
            oos.writeObject(mEnrolledTemplate);
            oos.close();
        } catch (FileNotFoundException e) {
            Log.e(TAG, e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void deleteEnrolledTemplateIfExists() {
        File enrolledFile = getFileStreamPath(ENROLL_FILENAME);
        if (enrolledFile.exists()) {
            enrolledFile.delete();
        }
    }

    private ProcessedBitmapCallback mProcessedCallback = new ProcessedBitmapCallback() {

        @Override
        public void onProcessedBitmapReady(Bitmap processedBitmap, CaptureMetrics metrics) {
            mCurrentFocusQuality = metrics.getFocusQuality();
            mFingerprintView.setImageBitmap(processedBitmap);
            mFingerprintView.startAnimation(mFadeIn);
        }

    };

    private WsqCallback mWsqCallback = new WsqCallback() {

        @Override
        public void onWsqReady(byte[] wsqData, CaptureMetrics metrics) {
            // TODO Do something with WSQ data
            Log.d(TAG, "NFIQ: " + metrics.getNfiqMetrics().getNfiqScore() + ", MLP: " + metrics.getNfiqMetrics().getMlpScore());
        }

    };

    private FingerprintTemplateCallback mTemplateCallback = new FingerprintTemplateCallback() {

        @Override
        public void onFingerprintTemplateReady(FingerprintTemplate fingerprintTemplate) {
            mCurrentTemplate = fingerprintTemplate;

            Log.d(TAG, "Template quality: " + mCurrentTemplate.getQuality());

            if (mEnrolledTemplate != null) {
                VerifyTask verifyTask = new VerifyTask(getApplicationContext());
                verifyTask.execute(mCurrentTemplate, mEnrolledTemplate);
            }
        }

    };

    private OnyxFragment.ErrorCallback mErrorCallback = new OnyxFragment.ErrorCallback() {

        @Override
        public void onError(Error error, String errorMessage, Exception exception) {
            switch (error) {
                case AUTOFOCUS_FAILURE:
                    mFragment.startOneShotAutoCapture();
                    break;
                default:
                    Log.d(TAG, "Error occurred: " + errorMessage);
                    break;
            }
        }

    };

    @Override
    public void onResume() {
        super.onResume();
        License lic = License.getInstance(this);
        try {
            lic.validate(getString(R.string.onyx_license));
        } catch (LicenseException e) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("License error")
                    .setMessage(e.getMessage())
                    .setNegativeButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
            builder.create().show();
        }
        loadEnrolledTemplateIfExists();
    }

    private void loadEnrolledTemplateIfExists() {
        File enrolledFile = getFileStreamPath(ENROLL_FILENAME);
        if (enrolledFile.exists()) {
            try {
                FileInputStream enrollStream = openFileInput(ENROLL_FILENAME);
                ObjectInputStream ois = new ObjectInputStream(enrollStream);
                mEnrolledTemplate = (FingerprintTemplate) ois.readObject();
            } catch (FileNotFoundException e) {
                Log.e(TAG, e.getMessage());
            } catch (StreamCorruptedException e) {
                Log.e(TAG, e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            } catch (ClassNotFoundException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.clear_enrollment:
                menuClearEnrollment();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void menuClearEnrollment() {
        Toast.makeText(this, "Clearing enrolled fingerprint.", Toast.LENGTH_SHORT).show();
        mEnrolledTemplate = null;
        deleteEnrolledTemplateIfExists();
    }
}
