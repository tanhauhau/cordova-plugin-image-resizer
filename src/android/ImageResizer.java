package info.protonet.imageresizer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class ImageResizer extends CordovaPlugin {
  private static final int ARGUMENT_NUMBER = 1;
  public CallbackContext callbackContext;

  private String uri;
  private String folderName;
  private int quality;
  private int width;
  private int height;

  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    try {
      this.callbackContext = callbackContext;

      if (action.equals("resize")) {
        checkParameters(args);

        // get the arguments
        JSONObject jsonObject = args.getJSONObject(0);
        uri = jsonObject.getString("uri");
        folderName = jsonObject.getString("folderName");
        quality = jsonObject.getInt("quality");
        width = jsonObject.getInt("width");
        height = jsonObject.getInt("height");

        // load the image from uri
        Bitmap bitmap = loadScaledBitmapFromUri(uri, width, height);

        // save the image as jpeg on the device
        Uri scaledFile = saveFile(bitmap);

        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, scaledFile.toString()));
        return true;
      } else {
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR));
        return false;
      }
    } catch(JSONException e) {
    	Log.e("Protonet", "JSON Exception during the Image Resizer Plugin... :(");
    }
    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR));
    return false;
  }

  /**
   * Loads a Bitmap of the given android uri path
   *
   * @params uri the URI who points to the image
   **/
  private Bitmap loadScaledBitmapFromUri(String uriString, int width, int height) {
    try {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      BitmapFactory.decodeStream(FileHelper.getInputStreamFromUriString(uriString, cordova), null, options);

      //calc aspect ratio
      int[] retval = calculateAspectRatio(options.outWidth, options.outHeight);

      options.inJustDecodeBounds = false;
      options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, width, height);
      Bitmap unscaledBitmap = BitmapFactory.decodeStream(FileHelper.getInputStreamFromUriString(uriString, cordova), null, options);
      return Bitmap.createScaledBitmap(unscaledBitmap, retval[0], retval[1], true);
    } catch (FileNotFoundException e) {
      Log.e("Protonet", "File not found. :(");
    } catch (IOException e) {
      Log.e("Protonet", "IO Exception :(");
    }catch(Exception e) {
      Log.e("Protonet", e.toString());
    }
    return null;
  }

  private Uri saveFile(Bitmap bitmap) {
    File folder = new File(Environment.getExternalStorageDirectory() + "/" + folderName);
    boolean success = true;
    if (!folder.exists()) {
      success = folder.mkdir();
    }

    if(success) {
      String fileName = System.currentTimeMillis() + ".jpg";
      File file = new File(folder, fileName);
      if(file.exists()) file.delete();
      try {
        FileOutputStream out = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        out.flush();
        out.close();
      } catch(Exception e) {
        Log.e("Protonet", e.toString());
      }
      return Uri.fromFile(file);
    }
    return null;
  }

  /**
   * Figure out what ratio we can load our image into memory at while still being bigger than
   * our desired width and height
   *
   * @param srcWidth
   * @param srcHeight
   * @param dstWidth
   * @param dstHeight
   * @return
   */
  private int calculateSampleSize(int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
    final float srcAspect = (float)srcWidth / (float)srcHeight;
    final float dstAspect = (float)dstWidth / (float)dstHeight;

    if (srcAspect > dstAspect) {
        return srcWidth / dstWidth;
    } else {
        return srcHeight / dstHeight;
    }
  }

  /**
   * Maintain the aspect ratio so the resulting image does not look smooshed
   *
   * @param origWidth
   * @param origHeight
   * @return
   */
  private int[] calculateAspectRatio(int origWidth, int origHeight) {
      int newWidth = width;
      int newHeight = height;

      // If no new width or height were specified return the original bitmap
      if (newWidth <= 0 && newHeight <= 0) {
          newWidth = origWidth;
          newHeight = origHeight;
      }
      // Only the width was specified
      else if (newWidth > 0 && newHeight <= 0) {
          newHeight = (newWidth * origHeight) / origWidth;
      }
      // only the height was specified
      else if (newWidth <= 0 && newHeight > 0) {
          newWidth = (newHeight * origWidth) / origHeight;
      }
      // If the user specified both a positive width and height
      // (potentially different aspect ratio) then the width or height is
      // scaled so that the image fits while maintaining aspect ratio.
      // Alternatively, the specified width and height could have been
      // kept and Bitmap.SCALE_TO_FIT specified when scaling, but this
      // would result in whitespace in the new image.
      else {
          double newRatio = newWidth / (double) newHeight;
          double origRatio = origWidth / (double) origHeight;

          if (origRatio > newRatio) {
              newHeight = (newWidth * origHeight) / origWidth;
          } else if (origRatio < newRatio) {
              newWidth = (newHeight * origWidth) / origHeight;
          }
      }

      int[] retval = new int[2];
      retval[0] = newWidth;
      retval[1] = newHeight;
      return retval;
  }

  private boolean checkParameters(JSONArray args) {
      if (args.length() != ARGUMENT_NUMBER) {
          callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
          return false;
      }
      return true;
  }
  /*
    FileHelper.java from apache/cordova-plugin-camera

       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at
         http://www.apache.org/licenses/LICENSE-2.0
       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */
    static class FileHelper {
        private static final String LOG_TAG = "FileUtils";
        private static final String _DATA = "_data";

        /**
         * Returns the real path of the given URI string.
         * If the given URI string represents a content:// URI, the real path is retrieved from the media store.
         *
         * @param uri the URI string of the audio/image/video
         * @param cordova the current application context
         * @return the full path to the file
         */
        @SuppressWarnings("deprecation")
        public static String getRealPath(Uri uri, CordovaInterface cordova) {
            String realPath = null;

            if (Build.VERSION.SDK_INT < 11)
                realPath = FileHelper.getRealPathFromURI_BelowAPI11(cordova.getActivity(), uri);

                // SDK >= 11 && SDK < 19
            else if (Build.VERSION.SDK_INT < 19)
                realPath = FileHelper.getRealPathFromURI_API11to18(cordova.getActivity(), uri);

                // SDK > 19 (Android 4.4)
            else
                realPath = FileHelper.getRealPathFromURI_API19(cordova.getActivity(), uri);

            return realPath;
        }

        /**
         * Returns the real path of the given URI.
         * If the given URI is a content:// URI, the real path is retrieved from the media store.
         *
         * @param uriString the URI of the audio/image/video
         * @param cordova the current application context
         * @return the full path to the file
         */
        public static String getRealPath(String uriString, CordovaInterface cordova) {
            return FileHelper.getRealPath(Uri.parse(uriString), cordova);
        }

        @SuppressLint("NewApi")
        public static String getRealPathFromURI_API19(Context context, Uri uri) {
            String filePath = "";
            try {
                String wholeID = DocumentsContract.getDocumentId(uri);

                // Split at colon, use second item in the array
                String id = wholeID.indexOf(":") > -1 ? wholeID.split(":")[1] : wholeID.indexOf(";") > -1 ? wholeID
                        .split(";")[1] : wholeID;

                String[] column = { MediaStore.Images.Media.DATA };

                // where id is equal to
                String sel = MediaStore.Images.Media._ID + "=?";

                Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, column,
                        sel, new String[] { id }, null);

                int columnIndex = cursor.getColumnIndex(column[0]);

                if (cursor.moveToFirst()) {
                    filePath = cursor.getString(columnIndex);
                }
                cursor.close();
            } catch (Exception e) {
                filePath = "";
            }
            return filePath;
        }

        @SuppressLint("NewApi")
        public static String getRealPathFromURI_API11to18(Context context, Uri contentUri) {
            String[] proj = { MediaStore.Images.Media.DATA };
            String result = null;

            try {
                CursorLoader cursorLoader = new CursorLoader(context, contentUri, proj, null, null, null);
                Cursor cursor = cursorLoader.loadInBackground();

                if (cursor != null) {
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    cursor.moveToFirst();
                    result = cursor.getString(column_index);
                }
            } catch (Exception e) {
                result = null;
            }
            return result;
        }

        public static String getRealPathFromURI_BelowAPI11(Context context, Uri contentUri) {
            String[] proj = { MediaStore.Images.Media.DATA };
            String result = null;

            try {
                Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                cursor.moveToFirst();
                result = cursor.getString(column_index);

            } catch (Exception e) {
                result = null;
            }
            return result;
        }

        /**
         * Returns an input stream based on given URI string.
         *
         * @param uriString the URI string from which to obtain the input stream
         * @param cordova the current application context
         * @return an input stream into the data at the given URI or null if given an invalid URI string
         * @throws IOException
         */
        public static InputStream getInputStreamFromUriString(String uriString, CordovaInterface cordova)
                throws IOException {
            InputStream returnValue = null;
            if (uriString.startsWith("content")) {
                Uri uri = Uri.parse(uriString);
                returnValue = cordova.getActivity().getContentResolver().openInputStream(uri);
            } else if (uriString.startsWith("file://")) {
                int question = uriString.indexOf("?");
                if (question > -1) {
                    uriString = uriString.substring(0, question);
                }
                if (uriString.startsWith("file:///android_asset/")) {
                    Uri uri = Uri.parse(uriString);
                    String relativePath = uri.getPath().substring(15);
                    returnValue = cordova.getActivity().getAssets().open(relativePath);
                } else {
                    // might still be content so try that first
                    try {
                        returnValue = cordova.getActivity().getContentResolver().openInputStream(Uri.parse(uriString));
                    } catch (Exception e) {
                        returnValue = null;
                    }
                    if (returnValue == null) {
                        returnValue = new FileInputStream(getRealPath(uriString, cordova));
                    }
                }
            } else {
                returnValue = new FileInputStream(uriString);
            }
            return returnValue;
        }

        /**
         * Removes the "file://" prefix from the given URI string, if applicable.
         * If the given URI string doesn't have a "file://" prefix, it is returned unchanged.
         *
         * @param uriString the URI string to operate on
         * @return a path without the "file://" prefix
         */
        public static String stripFileProtocol(String uriString) {
            if (uriString.startsWith("file://")) {
                uriString = uriString.substring(7);
            }
            return uriString;
        }

        public static String getMimeTypeForExtension(String path) {
            String extension = path;
            int lastDot = extension.lastIndexOf('.');
            if (lastDot != -1) {
                extension = extension.substring(lastDot + 1);
            }
            // Convert the URI string to lower case to ensure compatibility with MimeTypeMap (see CB-2185).
            extension = extension.toLowerCase(Locale.getDefault());
            if (extension.equals("3ga")) {
                return "audio/3gpp";
            }
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }

        /**
         * Returns the mime type of the data specified by the given URI string.
         *
         * @param uriString the URI string of the data
         * @return the mime type of the specified data
         */
        public static String getMimeType(String uriString, CordovaInterface cordova) {
            String mimeType = null;

            Uri uri = Uri.parse(uriString);
            if (uriString.startsWith("content://")) {
                mimeType = cordova.getActivity().getContentResolver().getType(uri);
            } else {
                mimeType = getMimeTypeForExtension(uri.getPath());
            }

            return mimeType;
        }
    }
}
