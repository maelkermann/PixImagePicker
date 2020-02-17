package com.fxn.pix;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fxn.adapters.InstantImageAdapter;
import com.fxn.adapters.MainImageAdapter;
import com.fxn.interfaces.OnSelectionListener;
import com.fxn.interfaces.WorkFinish;
import com.fxn.modals.Img;
import com.fxn.utility.Constants;
import com.fxn.utility.HeaderItemDecoration;
import com.fxn.utility.ImageFetcher;
import com.fxn.utility.ImageVideoFetcher;
import com.fxn.utility.PermUtil;
import com.fxn.utility.PixFileType;
import com.fxn.utility.Utility;
import com.fxn.utility.ui.FastScrollStateChangeListener;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.FileCallback;
import com.otaliastudios.cameraview.PictureResult;
import com.otaliastudios.cameraview.VideoResult;
import com.otaliastudios.cameraview.controls.Facing;
import com.otaliastudios.cameraview.controls.Flash;
import com.otaliastudios.cameraview.controls.Mode;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class Pix extends AppCompatActivity implements View.OnTouchListener {

  private static final int sBubbleAnimDuration = 1000;
  private static final int sScrollbarHideDelay = 1000;
  private static final String OPTIONS = "options";
  private static final int sTrackSnapRange = 5;
  public static String IMAGE_RESULTS = "image_results";
  public static float TOPBAR_HEIGHT;
CameraView camera;
  boolean camAvail = true;
  private int BottomBarHeight = 0;
  private int colorPrimaryDark;

  private float zoom = 0.0f;
  private float dist = 0.0f;
  private Handler handler = new Handler();
  private FastScrollStateChangeListener mFastScrollStateChangeListener;
  private RecyclerView recyclerView, instantRecyclerView;
  private BottomSheetBehavior mBottomSheetBehavior;
  private InstantImageAdapter initaliseadapter;
  private View status_bar_bg, mScrollbar, topbar, bottomButtons, sendButton;
  private TextView mBubbleView, img_count;
  private ImageView mHandleView, selection_back, selection_check;
  private ViewPropertyAnimator mScrollbarAnimator;
  private ViewPropertyAnimator mBubbleAnimator;
  private Set<Img> selectionList = new HashSet<>();
  private Runnable mScrollbarHider = new Runnable() {
    @Override
    public void run() {
      hideScrollbar();
    }
  };
  private MainImageAdapter mainImageAdapter;
  private float mViewHeight;
  private boolean mHideScrollbar = true;
  private boolean LongSelection = false;
  private Options options = null;
int status_bar_height = 0;
private TextView selection_count;
  private RecyclerView.OnScrollListener mScrollListener = new RecyclerView.OnScrollListener() {

    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
      if (!mHandleView.isSelected() && recyclerView.isEnabled()) {
        setViewPositions(getScrollProportion(recyclerView));
      }
    }

    @Override
    public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
      super.onScrollStateChanged(recyclerView, newState);

      if (recyclerView.isEnabled()) {
        switch (newState) {
          case RecyclerView.SCROLL_STATE_DRAGGING:
            handler.removeCallbacks(mScrollbarHider);
	          if (mScrollbar.getVisibility() != View.VISIBLE) {
		          Utility.cancelAnimation(mScrollbarAnimator);
		          if (!Utility.isViewVisible(mScrollbar) && (recyclerView.computeVerticalScrollRange()
				          - mViewHeight > 0)) {

			          mScrollbarAnimator = Utility.showScrollbar(mScrollbar, Pix.this);
		          }
	          }
            break;
          case RecyclerView.SCROLL_STATE_IDLE:
            if (mHideScrollbar && !mHandleView.isSelected()) {
              handler.postDelayed(mScrollbarHider, sScrollbarHideDelay);
            }
            break;
          default:
            break;
        }
      }
    }
  };

  private FrameLayout flash;
  private ImageView front;
  private int flashDrawable;
  private View.OnTouchListener onCameraTouchListner = new View.OnTouchListener() {
    @Override
    public boolean onTouch(View v, MotionEvent event) {
      if (event.getPointerCount() > 1) {

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
          case MotionEvent.ACTION_POINTER_DOWN:
            dist = Utility.getFingerSpacing(event);
            break;
          case MotionEvent.ACTION_MOVE:
            float maxZoom = 1f;

            float newDist = Utility.getFingerSpacing(event);
            if (newDist > dist) {
              //zoom in
              if (zoom < maxZoom) {
                zoom = zoom + 0.01f;
              }
            } else if ((newDist < dist) && (zoom > 0)) {
              //zoom out
              zoom = zoom - 0.01f;
            }
            dist = newDist;
	          camera.setZoom(zoom);
            break;
          default:
            break;
        }
      }
      return false;
    }
  };

  public static void start(final Fragment context, final Options options) {
    PermUtil.checkForCamaraWritePermissions(context, new WorkFinish() {
      @Override
      public void onWorkFinish(Boolean check) {
        Intent i = new Intent(context.getActivity(), Pix.class);
        i.putExtra(OPTIONS, options);
        context.startActivityForResult(i, options.getRequestCode());
      }
    });
  }

  public static void start(Fragment context, int requestCode) {
    start(context, Options.init().setRequestCode(requestCode).setCount(1));
  }

  public static void start(final FragmentActivity context, final Options options) {
    PermUtil.checkForCamaraWritePermissions(context, new WorkFinish() {
      @Override
      public void onWorkFinish(Boolean check) {
        Intent i = new Intent(context, Pix.class);
        i.putExtra(OPTIONS, options);
        context.startActivityForResult(i, options.getRequestCode());
      }
    });
  }

  public static void start(final FragmentActivity context, int requestCode) {
    start(context, Options.init().setRequestCode(requestCode).setCount(1));
  }

  private void hideScrollbar() {
    float transX = getResources().getDimensionPixelSize(R.dimen.fastscroll_scrollbar_padding_end);
    mScrollbarAnimator = mScrollbar.animate().translationX(transX).alpha(0f)
        .setDuration(Constants.sScrollbarAnimDuration)
        .setListener(new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            mScrollbar.setVisibility(View.GONE);
            mScrollbarAnimator = null;
          }

          @Override
          public void onAnimationCancel(Animator animation) {
            super.onAnimationCancel(animation);
            mScrollbar.setVisibility(View.GONE);
            mScrollbarAnimator = null;
          }
        });
  }

  public void returnObjects() {
    ArrayList<String> list = new ArrayList<>();
    for (Img i : selectionList) {
      list.add(i.getUrl());
      // Log.e("Pix images", "img " + i.getUrl());
    }
    Intent resultIntent = new Intent();
    resultIntent.putStringArrayListExtra(IMAGE_RESULTS, list);
    setResult(Activity.RESULT_OK, resultIntent);
    finish();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Utility.setupStatusBarHidden(this);
    Utility.hideStatusBar(this);
    setContentView(R.layout.activity_main_lib);
    initialize();
  }

  @Override
  protected void onRestart() {
    super.onRestart();
	  camera.open();
	  camera.setMode(Mode.PICTURE);
  }

  @Override
  protected void onResume() {
    super.onResume();
	  camera.open();
	  camera.setMode(Mode.PICTURE);
  }

  @Override
  protected void onPause() {
	  camera.close();
    super.onPause();
  }

private OnSelectionListener onSelectionListener = new OnSelectionListener() {
	@Override
	public void onClick(Img img, View view, int position) {
		if (LongSelection) {
			if (selectionList.contains(img)) {
				selectionList.remove(img);
				initaliseadapter.select(false, position);
				mainImageAdapter.select(false, position);
			} else {
				if (options.getCount() <= selectionList.size()) {
					Toast.makeText(Pix.this,
							String.format(getResources().getString(R.string.selection_limiter_pix),
									selectionList.size()), Toast.LENGTH_SHORT).show();
					return;
				}
				img.setPosition(position);
				selectionList.add(img);
				initaliseadapter.select(true, position);
				mainImageAdapter.select(true, position);
			}
			if (selectionList.size() == 0) {
				LongSelection = false;
				selection_check.setVisibility(View.VISIBLE);
				DrawableCompat.setTint(selection_back.getDrawable(), colorPrimaryDark);
				topbar.setBackgroundColor(Color.parseColor("#ffffff"));
				Animation anim = new ScaleAnimation(
						1f, 0f, // Start and end values for the X axis scaling
						1f, 0f, // Start and end values for the Y axis scaling
						Animation.RELATIVE_TO_SELF, 0.5f, // Pivot point of X scaling
						Animation.RELATIVE_TO_SELF, 0.5f); // Pivot point of Y scaling
				anim.setFillAfter(true); // Needed to keep the result of the animation
				anim.setDuration(300);
				anim.setAnimationListener(new Animation.AnimationListener() {

					@Override
					public void onAnimationStart(Animation animation) {

					}

					@Override
					public void onAnimationEnd(Animation animation) {
						sendButton.setVisibility(View.GONE);
						sendButton.clearAnimation();
					}

					@Override
					public void onAnimationRepeat(Animation animation) {

					}
				});
				sendButton.startAnimation(anim);
			}
			selection_count.setText(selectionList.size() + " " +
					getResources().getString(R.string.pix_selected));
			img_count.setText(String.valueOf(selectionList.size()));
		} else {
			img.setPosition(position);
			selectionList.add(img);
			returnObjects();
			DrawableCompat.setTint(selection_back.getDrawable(), colorPrimaryDark);
			topbar.setBackgroundColor(Color.parseColor("#ffffff"));
		}
	}

	@Override
	public void onLongClick(Img img, View view, int position) {
		if (options.getCount() > 1) {
			Utility.vibe(Pix.this, 50);
			//Log.e("onLongClick", "onLongClick");
			LongSelection = true;
			if ((selectionList.size() == 0) && (mBottomSheetBehavior.getState()
					!= BottomSheetBehavior.STATE_EXPANDED)) {
				sendButton.setVisibility(View.VISIBLE);
				Animation anim = new ScaleAnimation(
						0f, 1f, // Start and end values for the X axis scaling
						0f, 1f, // Start and end values for the Y axis scaling
						Animation.RELATIVE_TO_SELF, 0.5f, // Pivot point of X scaling
						Animation.RELATIVE_TO_SELF, 0.5f); // Pivot point of Y scaling
				anim.setFillAfter(true); // Needed to keep the result of the animation
				anim.setDuration(300);
				sendButton.startAnimation(anim);
			}
			if (selectionList.contains(img)) {
				selectionList.remove(img);
				initaliseadapter.select(false, position);
				mainImageAdapter.select(false, position);
			} else {
				if (options.getCount() <= selectionList.size()) {
					Toast.makeText(Pix.this,
							String.format(getResources().getString(R.string.selection_limiter_pix),
									selectionList.size()), Toast.LENGTH_SHORT).show();
					return;
				}
				img.setPosition(position);
				selectionList.add(img);
				initaliseadapter.select(true, position);
				mainImageAdapter.select(true, position);
			}
			selection_check.setVisibility(View.GONE);
			topbar.setBackgroundColor(colorPrimaryDark);
			selection_count.setText(selectionList.size() + " " +
					getResources().getString(R.string.pix_selected));
			img_count.setText(String.valueOf(selectionList.size()));
			DrawableCompat.setTint(selection_back.getDrawable(), Color.parseColor("#ffffff"));
		}
	}
};

  private void initialize() {
	  WindowManager.LayoutParams params = getWindow().getAttributes();
	  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
		  params.layoutInDisplayCutoutMode =
				  WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
	  }
    Utility.getScreenSize(this);
    if (getSupportActionBar() != null) {
      getSupportActionBar().hide();
    }
    try {
      options = (Options) getIntent().getSerializableExtra(OPTIONS);
    } catch (Exception e) {
      e.printStackTrace();
    }
	  status_bar_height = Utility.getStatusBarSizePort(Pix.this);
    setRequestedOrientation(options.getScreenOrientation());
    colorPrimaryDark =
        ResourcesCompat.getColor(getResources(), R.color.colorPrimaryPix, getTheme());
	  camera = findViewById(R.id.camera_view);
	 /* camera.getLayoutParams().width = Utility.WIDTH;
	  camera.getLayoutParams().height = Utility.HEIGHT;
	  camera.requestLayout();*/
	  camera.setLifecycleOwner(Pix.this);
	  camera.setMode(Mode.PICTURE);
	  camera.addCameraListener(new CameraListener() {
		  @Override
		  public void onPictureTaken(PictureResult result) {
			  // A Picture was taken!
			  /* Picture was taken!
        // If planning to show a Bitmap, we will take care of
        // EXIF rotation and background threading for you...
        ;

        // If planning to save a file on a background thread,
        // just use toFile. Ensure you have permissions.


        // Access the raw data if needed.
        byte[] data = result.getData();*/
			  Log.e("CLICK ", "IMAGE ");
			  File dir = new File(Environment.getExternalStorageDirectory(), options.getPath());
			  if (!dir.exists()) {
				  dir.mkdirs();
			  }
			  File photo = new File(dir, "IMG_"
					  + new SimpleDateFormat("yyyyMMdd_HHmmSS", Locale.ENGLISH).format(new Date())
					  + ".jpg");

			  result.toFile(photo, new FileCallback() {
				  @Override public void onFileReady(@Nullable File photo) {
					  Utility.vibe(Pix.this, 50);
					  Log.e("CLICK ", "IMAGE " + photo.getPath());
					  Img img = new Img("", "", photo.getAbsolutePath(), "", PixFileType.IMAGE);
					  selectionList.add(img);
					  Utility.scanPhoto(Pix.this, photo);
					  Log.e("CLICK ", "IMAGE SCAN");
					  Log.e("click time", "--------------------------------2");
					  returnObjects();
				  }
			  });
			/* result.toBitmap(options.getWidth(), options.getHeight(), new BitmapCallback() {
				  @Override public void onBitmapReady(@Nullable Bitmap bitmap) {
					  Log.e("CLICK ","IMAGE bitmap");
					  if (bitmap != null) {
						  synchronized (bitmap) {
							  Utility.vibe(Pix.this, 50);
							  File photo =
									  Utility.writeImage(bitmap, options.getPath(), options.getImageQuality(),
											  options.getWidth(), options.getHeight());
							  Log.e("CLICK ","IMAGE bitmap "+photo.getAbsolutePath());

							  Img img = new Img("", "", photo.getAbsolutePath(), "");
							  selectionList.add(img);
							  Utility.scanPhoto(Pix.this, photo);
							  Log.e("CLICK ","IMAGE SCAN");
							  Log.e("click time", "--------------------------------2");
							  returnObjects();
						  }
					  }
				  }
			  });*/

		  }

		  @Override
		  public void onVideoTaken(VideoResult result) {
			  // A Video was taken!
		  }

		  // And much more
	  });
    zoom = 0.0f;
	  // camera.setOnTouchListener(onCameraTouchListner);
    flash = findViewById(R.id.flash);
    front = findViewById(R.id.front);
    topbar = findViewById(R.id.topbar);
    selection_count = findViewById(R.id.selection_count);
    selection_back = findViewById(R.id.selection_back);
    selection_check = findViewById(R.id.selection_check);
    selection_check.setVisibility((options.getCount() > 1) ? View.VISIBLE : View.GONE);
    sendButton = findViewById(R.id.sendButton);
    img_count = findViewById(R.id.img_count);
    mBubbleView = findViewById(R.id.fastscroll_bubble);
    mHandleView = findViewById(R.id.fastscroll_handle);
    mScrollbar = findViewById(R.id.fastscroll_scrollbar);
    mScrollbar.setVisibility(View.GONE);
    mBubbleView.setVisibility(View.GONE);
    bottomButtons = findViewById(R.id.bottomButtons);
    TOPBAR_HEIGHT = Utility.convertDpToPixel(56, Pix.this);
    status_bar_bg = findViewById(R.id.status_bar_bg);

	  Log.e("status_bar_bg", "->   " + status_bar_height);
	  status_bar_bg.getLayoutParams().height = status_bar_height;
	  status_bar_bg.setTranslationY(-1 * status_bar_height);
	  status_bar_bg.requestLayout();
    instantRecyclerView = findViewById(R.id.instantRecyclerView);
    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
    linearLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
    instantRecyclerView.setLayoutManager(linearLayoutManager);
    initaliseadapter = new InstantImageAdapter(this);
    initaliseadapter.addOnSelectionListener(onSelectionListener);
    instantRecyclerView.setAdapter(initaliseadapter);
    recyclerView = findViewById(R.id.recyclerView);
    recyclerView.addOnScrollListener(mScrollListener);
    FrameLayout mainFrameLayout = findViewById(R.id.mainFrameLayout);
	  CoordinatorLayout main_content = findViewById(R.id.main_content);
    BottomBarHeight = Utility.getSoftButtonsBarSizePort(this);
   /* FrameLayout.LayoutParams lp =
        new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
    lp.setMargins(0, 0, 0, BottomBarHeight);*/
	  FrameLayout.LayoutParams lp1 =
        new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
	  lp1.setMargins(0, status_bar_height, 0, 0);
	  main_content.setLayoutParams(lp1);
	  //mainFrameLayout.setLayoutParams(lp);
    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) sendButton.getLayoutParams();
    layoutParams.setMargins(0, 0, (int) (Utility.convertDpToPixel(16, this)),
        (int) (Utility.convertDpToPixel(174, this)));
    sendButton.setLayoutParams(layoutParams);
    mainImageAdapter = new MainImageAdapter(this);
    GridLayoutManager mLayoutManager = new GridLayoutManager(this, MainImageAdapter.SPAN_COUNT);
    mLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize(int position) {
        if (mainImageAdapter.getItemViewType(position) == MainImageAdapter.HEADER) {
          return MainImageAdapter.SPAN_COUNT;
        }
        return 1;
      }
    });
    recyclerView.setLayoutManager(mLayoutManager);
    mainImageAdapter.addOnSelectionListener(onSelectionListener);
    recyclerView.setAdapter(mainImageAdapter);
    recyclerView.addItemDecoration(new HeaderItemDecoration(this, mainImageAdapter));
    mHandleView.setOnTouchListener(this);
   /* final CameraConfiguration cameraConfiguration = new CameraConfiguration();
    if (options.isFrontfacing()) {
      fotoapparat.switchTo(LensPositionSelectorsKt.front(), cameraConfiguration);
    } else {
      fotoapparat.switchTo(LensPositionSelectorsKt.back(), cameraConfiguration);
    }*/
    onClickMethods();

    flashDrawable = R.drawable.ic_flash_off_black_24dp;

    if ((options.getPreSelectedUrls().size()) > options.getCount()) {
      int large = options.getPreSelectedUrls().size() - 1;
      int small = options.getCount();
      for (int i = large; i > (small - 1); i--) {
        options.getPreSelectedUrls().remove(i);
      }
    }
    DrawableCompat.setTint(selection_back.getDrawable(), colorPrimaryDark);
    updateImages();
  }

  private void onClickMethods() {
    findViewById(R.id.clickme).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
	      Log.e("CLICK", "IMAGE INIT");

        if (selectionList.size() >= options.getCount()) {
          Toast.makeText(Pix.this,
              String.format(getResources().getString(R.string.cannot_click_image_pix),
                  "" + options.getCount()), Toast.LENGTH_LONG).show();
          return;
        }
	      /*SizeSelector width = SizeSelectors.minWidth(Utility.WIDTH);
	      SizeSelector height = SizeSelectors.minHeight(Utility.HEIGHT);
	      SizeSelector dimensions = SizeSelectors.and(width, height); // Matches sizes bigger than 1000x2000.
	      SizeSelector ratio = SizeSelectors.aspectRatio(AspectRatio.of(Utility.WIDTH, Utility.HEIGHT), 0); // Matches 1:1 sizes.

	      SizeSelector result = SizeSelectors.or(
			      SizeSelectors.and(ratio, dimensions), // Try to match both constraints
			      ratio, // If none is found, at least try to match the aspect ratio
			      SizeSelectors.biggest() // If none is found, take the biggest
	      );*/
	      //camera.setPictureSize(result);
	      final ObjectAnimator oj = ObjectAnimator.ofFloat(camera, "alpha", 1f, 0f, 0f, 1f);
	      oj.setStartDelay(200l);
	      oj.setDuration(600l);
	      oj.start();
	      camera.takePicture();
	      return;

      /*  final ObjectAnimator oj = ObjectAnimator.ofFloat(cameraView, "alpha", 1f, 0f, 0f, 1f);
        oj.setStartDelay(200l);
        oj.setDuration(900l);
        oj.start();*/
	      //  Log.e("click time", "--------------------------------");
     /*   fotoapparat.takePicture().toBitmap().transform(new Function1<BitmapPhoto, Bitmap>() {
          @Override
          public Bitmap invoke(BitmapPhoto bitmapPhoto) {
            Log.e("click time", "--------------------------------1");
            return Utility.rotate(bitmapPhoto.bitmap, bitmapPhoto.rotationDegrees);
          }
        }).whenAvailable(new Function1<Bitmap, Unit>() {
          @Override
          public Unit invoke(Bitmap bitmap) {
            if (bitmap != null) {
              synchronized (bitmap) {
                Utility.vibe(Pix.this, 50);
                File photo =
                    Utility.writeImage(bitmap, options.getPath(), options.getImageQuality(),
                        options.getWidth(), options.getHeight());
                Img img = new Img("", "", photo.getAbsolutePath(), "");
                selectionList.add(img);
                Utility.scanPhoto(Pix.this, photo);
                Log.e("click time", "--------------------------------2");
                returnObjects();
              }
            }
            return null;
          }
        });*/
      }
    });
    findViewById(R.id.selection_ok).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        // Toast.makeText(Pix.this, "fin", Toast.LENGTH_SHORT).show();
        //Log.e("Hello", "onclick");
        returnObjects();
      }
    });
    sendButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        //Toast.makeText(Pix.this, "fin", Toast.LENGTH_SHORT).show();
        //Log.e("Hello", "onclick");
        returnObjects();
      }
    });
    selection_back.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
      }
    });
    selection_check.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        topbar.setBackgroundColor(colorPrimaryDark);
        selection_count.setText(getResources().getString(R.string.pix_tap_to_select));
        img_count.setText(String.valueOf(selectionList.size()));
        DrawableCompat.setTint(selection_back.getDrawable(), Color.parseColor("#ffffff"));
        LongSelection = true;
        selection_check.setVisibility(View.GONE);
      }
    });
    final ImageView iv = (ImageView) flash.getChildAt(0);
    flash.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        final int height = flash.getHeight();
        iv.animate()
            .translationY(height)
            .setDuration(100)
            .setListener(new AnimatorListenerAdapter() {
              @Override
              public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                iv.setTranslationY(-(height / 2));
                if (flashDrawable == R.drawable.ic_flash_auto_black_24dp) {
                  flashDrawable = R.drawable.ic_flash_off_black_24dp;
                  iv.setImageResource(flashDrawable);
	                camera.setFlash(Flash.OFF);
	                //fotoapparat.updateConfiguration(
	                //  CameraConfiguration.builder().flash(FlashSelectorsKt.off()).build());
                } else if (flashDrawable == R.drawable.ic_flash_off_black_24dp) {
                  flashDrawable = R.drawable.ic_flash_on_black_24dp;
                  iv.setImageResource(flashDrawable);
	                camera.setFlash(Flash.ON);
	                //   fotoapparat.updateConfiguration(
	                //     CameraConfiguration.builder().flash(FlashSelectorsKt.on()).build());
                } else {
                  flashDrawable = R.drawable.ic_flash_auto_black_24dp;
                  iv.setImageResource(flashDrawable);
	                camera.setFlash(Flash.AUTO);
	                // fotoapparat.updateConfiguration(
	                //   CameraConfiguration.builder().flash(FlashSelectorsKt.autoRedEye()).build());
                }
                // fotoapparat.focus();

                iv.animate().translationY(0).setDuration(50).setListener(null).start();
              }
            })
            .start();
      }
    });

    front.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        final ObjectAnimator oa1 = ObjectAnimator.ofFloat(front, "scaleX", 1f, 0f).setDuration(150);
        final ObjectAnimator oa2 = ObjectAnimator.ofFloat(front, "scaleX", 0f, 1f).setDuration(150);
        oa1.addListener(new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            front.setImageResource(R.drawable.ic_photo_camera);
            oa2.start();
          }
        });
        oa1.start();
        if (options.isFrontfacing()) {
          options.setFrontfacing(false);
	        camera.setFacing(Facing.BACK);
	        //  final CameraConfiguration cameraConfiguration = new CameraConfiguration();
	        //fotoapparat.switchTo(LensPositionSelectorsKt.back(), cameraConfiguration);
        } else {
	        camera.setFacing(Facing.FRONT);
	        //final CameraConfiguration cameraConfiguration = new CameraConfiguration();
          options.setFrontfacing(true);
	        //fotoapparat.switchTo(LensPositionSelectorsKt.front(), cameraConfiguration);
        }
      }
    });
  }

  private void updateImages() {
	  ImageVideoFetcher imageVideoFetcher = new ImageVideoFetcher(Pix.this) {
		  @Override protected void onPostExecute(ModelList modelList) {
			  super.onPostExecute(modelList);
		  }
	  };

	  imageVideoFetcher.execute(Utility.getImageVideoCursor(Pix.this));
	  imageVideoFetcher.setStartingCount(0);
	  imageVideoFetcher.header = "";
	  //imageVideoFetcher.setPreSelectedUrls(options.getPreSelectedUrls());
    mainImageAdapter.clearList();
    Cursor cursor = Utility.getCursor(Pix.this);
    if (cursor == null) {
      return;
    }
    ArrayList<Img> INSTANTLIST = new ArrayList<>();
    String header = "";
    int limit = 100;
    if (cursor.getCount() < limit) {
      limit = cursor.getCount() - 1;
    }
    int date = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN);
    int data = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
    int contentUrl = cursor.getColumnIndex(MediaStore.Images.Media._ID);
    Calendar calendar;
    int pos = 0;
    for (int i = 0; i < limit; i++) {
      cursor.moveToNext();
      Uri path = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
          "" + cursor.getInt(contentUrl));
      calendar = Calendar.getInstance();
      calendar.setTimeInMillis(cursor.getLong(date));
      String dateDifference = Utility.getDateDifference(Pix.this, calendar);
      if (!header.equalsIgnoreCase("" + dateDifference)) {
        header = "" + dateDifference;
        pos += 1;
	      INSTANTLIST.add(new Img("" + dateDifference, "", "", "", PixFileType.IMAGE));
      }
	    Img img =
			    new Img("" + header, "" + path, cursor.getString(data), "" + pos, PixFileType.IMAGE);
      img.setPosition(pos);
      if (options.getPreSelectedUrls().contains(img.getUrl())) {
        img.setSelected(true);
        selectionList.add(img);
      }
      pos += 1;
      INSTANTLIST.add(img);
    }
    if (selectionList.size() > 0) {
      LongSelection = true;
      sendButton.setVisibility(View.VISIBLE);
      Animation anim = new ScaleAnimation(
          0f, 1f, // Start and end values for the X axis scaling
          0f, 1f, // Start and end values for the Y axis scaling
          Animation.RELATIVE_TO_SELF, 0.5f, // Pivot point of X scaling
          Animation.RELATIVE_TO_SELF, 0.5f); // Pivot point of Y scaling
      anim.setFillAfter(true); // Needed to keep the result of the animation
      anim.setDuration(300);
      sendButton.startAnimation(anim);
      selection_check.setVisibility(View.GONE);
      topbar.setBackgroundColor(colorPrimaryDark);
	    selection_count.setText(selectionList.size() + " " +
			    getResources().getString(R.string.pix_selected));
      img_count.setText(String.valueOf(selectionList.size()));
      DrawableCompat.setTint(selection_back.getDrawable(), Color.parseColor("#ffffff"));
    }
    mainImageAdapter.addImageList(INSTANTLIST);
    initaliseadapter.addImageList(INSTANTLIST);
    ImageFetcher imageFetcher = new ImageFetcher(Pix.this) {
      @Override
      protected void onPostExecute(ImageFetcher.ModelList imgs) {
        super.onPostExecute(imgs);
        mainImageAdapter.addImageList(imgs.getLIST());
        initaliseadapter.addImageList(imgs.getLIST());
        selectionList.addAll(imgs.getSelection());
        if (selectionList.size() > 0) {
          LongSelection = true;
          sendButton.setVisibility(View.VISIBLE);
          Animation anim = new ScaleAnimation(
              0f, 1f, // Start and end values for the X axis scaling
              0f, 1f, // Start and end values for the Y axis scaling
              Animation.RELATIVE_TO_SELF, 0.5f, // Pivot point of X scaling
              Animation.RELATIVE_TO_SELF, 0.5f); // Pivot point of Y scaling
          anim.setFillAfter(true); // Needed to keep the result of the animation
          anim.setDuration(300);
          sendButton.startAnimation(anim);
          selection_check.setVisibility(View.GONE);
          topbar.setBackgroundColor(colorPrimaryDark);
	        selection_count.setText(selectionList.size() + " " +
			        getResources().getString(R.string.pix_selected));
          img_count.setText(String.valueOf(selectionList.size()));
          DrawableCompat.setTint(selection_back.getDrawable(), Color.parseColor("#ffffff"));
        }
      }
    };
    imageFetcher.setStartingCount(pos);
    imageFetcher.header = header;
    imageFetcher.setPreSelectedUrls(options.getPreSelectedUrls());
    imageFetcher.execute(Utility.getCursor(Pix.this));
    cursor.close();
    setBottomSheetBehavior();
  }

  private void setBottomSheetBehavior() {
    View bottomSheet = findViewById(R.id.bottom_sheet);
    mBottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
    mBottomSheetBehavior.setPeekHeight((int) (Utility.convertDpToPixel(194, this)));
    mBottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {

      @Override
      public void onStateChanged(@NonNull View bottomSheet, int newState) {

      }

      @Override
      public void onSlide(@NonNull View bottomSheet, float slideOffset) {
        Utility.manipulateVisibility(Pix.this, slideOffset,
            instantRecyclerView, recyclerView, status_bar_bg,
            topbar, bottomButtons, sendButton, LongSelection);
        if (slideOffset == 1) {
          Utility.showScrollbar(mScrollbar, Pix.this);
          mainImageAdapter.notifyDataSetChanged();
          mViewHeight = mScrollbar.getMeasuredHeight();
          handler.post(new Runnable() {
            @Override
            public void run() {
              setViewPositions(getScrollProportion(recyclerView));
            }
          });
          sendButton.setVisibility(View.GONE);
          //  fotoapparat.stop();
        } else if (slideOffset == 0) {
          initaliseadapter.notifyDataSetChanged();
          hideScrollbar();
          img_count.setText(String.valueOf(selectionList.size()));
	        camera.open();
        }
      }
    });
  }

  private float getScrollProportion(RecyclerView recyclerView) {
    final int verticalScrollOffset = recyclerView.computeVerticalScrollOffset();
    final int verticalScrollRange = recyclerView.computeVerticalScrollRange();
    final float rangeDiff = verticalScrollRange - mViewHeight;
    float proportion = (float) verticalScrollOffset / (rangeDiff > 0 ? rangeDiff : 1f);
    return mViewHeight * proportion;
  }

  private void setViewPositions(float y) {
    int handleY = Utility.getValueInRange(0, (int) (mViewHeight - mHandleView.getHeight()),
        (int) (y - mHandleView.getHeight() / 2));
	  mBubbleView.setY(handleY + Utility.convertDpToPixel(60, this));
    mHandleView.setY(handleY);
  }

  private void setRecyclerViewPosition(float y) {
    if (recyclerView != null && recyclerView.getAdapter() != null) {
      int itemCount = recyclerView.getAdapter().getItemCount();
      float proportion;

      if (mHandleView.getY() == 0) {
        proportion = 0f;
      } else if (mHandleView.getY() + mHandleView.getHeight() >= mViewHeight - sTrackSnapRange) {
        proportion = 1f;
      } else {
        proportion = y / mViewHeight;
      }

      int scrolledItemCount = Math.round(proportion * itemCount);
      int targetPos = Utility.getValueInRange(0, itemCount - 1, scrolledItemCount);
      recyclerView.getLayoutManager().scrollToPosition(targetPos);

      if (mainImageAdapter != null) {
        String text = mainImageAdapter.getSectionMonthYearText(targetPos);
        mBubbleView.setText(text);
        if (text.equalsIgnoreCase("")) {
          mBubbleView.setVisibility(View.GONE);
        }
      }
    }
  }

  private void showBubble() {
    if (!Utility.isViewVisible(mBubbleView)) {
      mBubbleView.setVisibility(View.VISIBLE);
      mBubbleView.setAlpha(0f);
      mBubbleAnimator = mBubbleView
          .animate()
          .alpha(1f)
          .setDuration(sBubbleAnimDuration)
          .setListener(new AnimatorListenerAdapter() {
            // adapter required for new alpha value to stick
          });
      mBubbleAnimator.start();
    }
  }

  private void hideBubble() {
    if (Utility.isViewVisible(mBubbleView)) {
      mBubbleAnimator = mBubbleView.animate().alpha(0f)
          .setDuration(sBubbleAnimDuration)
          .setListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
              super.onAnimationEnd(animation);
              mBubbleView.setVisibility(View.GONE);
              mBubbleAnimator = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
              super.onAnimationCancel(animation);
              mBubbleView.setVisibility(View.GONE);
              mBubbleAnimator = null;
            }
          });
      mBubbleAnimator.start();
    }
  }

  @Override
  public boolean onTouch(View view, MotionEvent event) {
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        if (event.getX() < mHandleView.getX() - ViewCompat.getPaddingStart(mHandleView)) {
          return false;
        }
        mHandleView.setSelected(true);
        handler.removeCallbacks(mScrollbarHider);
        Utility.cancelAnimation(mScrollbarAnimator);
        Utility.cancelAnimation(mBubbleAnimator);

        if (!Utility.isViewVisible(mScrollbar) && (recyclerView.computeVerticalScrollRange()
            - mViewHeight > 0)) {
          mScrollbarAnimator = Utility.showScrollbar(mScrollbar, Pix.this);
        }

        if (mainImageAdapter != null) {
          showBubble();
        }

        if (mFastScrollStateChangeListener != null) {
          mFastScrollStateChangeListener.onFastScrollStart(this);
        }
      case MotionEvent.ACTION_MOVE:
        final float y = event.getRawY();
             /*   String text = mainImageAdapter.getSectionText(recyclerView.getVerticalScrollbarPosition()).trim();
                mBubbleView.setText("hello------>"+text+"<--");
                if (text.equalsIgnoreCase("")) {
                    mBubbleView.setVisibility(View.GONE);
                }
                Log.e("hello"," -->> "+ mBubbleView.getText());*/
        setViewPositions(y - TOPBAR_HEIGHT);
        setRecyclerViewPosition(y);
        return true;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        mHandleView.setSelected(false);
        if (mHideScrollbar) {
          handler.postDelayed(mScrollbarHider, sScrollbarHideDelay);
        }
        hideBubble();
        if (mFastScrollStateChangeListener != null) {
          mFastScrollStateChangeListener.onFastScrollStop(this);
        }
        return true;
    }
    return super.onTouchEvent(event);
  }

  @Override
  public void onBackPressed() {

    if (selectionList.size() > 0) {
      for (Img img : selectionList) {
        options.setPreSelectedUrls(new ArrayList<String>());
        mainImageAdapter.getItemList().get(img.getPosition()).setSelected(false);
        mainImageAdapter.notifyItemChanged(img.getPosition());
        initaliseadapter.getItemList().get(img.getPosition()).setSelected(false);
        initaliseadapter.notifyItemChanged(img.getPosition());
      }
      LongSelection = false;
      if (options.getCount() > 1) {
        selection_check.setVisibility(View.VISIBLE);
      }
      DrawableCompat.setTint(selection_back.getDrawable(), colorPrimaryDark);
      topbar.setBackgroundColor(Color.parseColor("#ffffff"));
      Animation anim = new ScaleAnimation(
          1f, 0f, // Start and end values for the X axis scaling
          1f, 0f, // Start and end values for the Y axis scaling
          Animation.RELATIVE_TO_SELF, 0.5f, // Pivot point of X scaling
          Animation.RELATIVE_TO_SELF, 0.5f); // Pivot point of Y scaling
      anim.setFillAfter(true); // Needed to keep the result of the animation
      anim.setDuration(300);
      anim.setAnimationListener(new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {

        }

        @Override
        public void onAnimationEnd(Animation animation) {
          sendButton.setVisibility(View.GONE);
          sendButton.clearAnimation();
        }

        @Override
        public void onAnimationRepeat(Animation animation) {

        }
      });
      sendButton.startAnimation(anim);
      selectionList.clear();
    } else if (mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
      mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    } else {
      super.onBackPressed();
    }
  }

  @Override protected void onDestroy() {
    super.onDestroy();
	  camera.destroy();
  }
}
