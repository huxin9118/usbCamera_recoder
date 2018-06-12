package org.uvccamera.playback;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.uvccamera.R;
import org.uvccamera.base.BaseRecyclerViewAdapter;
import org.uvccamera.base.OnItemClickListener;
import org.uvccamera.base.OnItemLongClickListener;
import org.uvccamera.bean.MediaInfo;
import org.uvccamera.preview.LocalUVCPreviewActivity;
import org.uvccamera.utils.ConstantUtils;
import org.uvccamera.utils.FileUtils;
import org.uvccamera.utils.ImageUtils;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by h26376 on 2017/9/1.
 */

public class FileListActivity extends AppCompatActivity implements ActionMode.Callback{
    private static final String TAG = "MAIN";
    private Toast mToast;
    private final String PERMISSION_WRITE_EXTERNAL_STORAGE= "android.permission.WRITE_EXTERNAL_STORAGE";
    private final int PERMISSION_REQUESTCODE = 0;
    private final int GET_CONTENT_REQUESTCODE = 1;
    private final int REFRESH_UI = 2;
    private RelativeLayout root;
    private FloatingActionButton fab;
    private Toolbar toolbar;
    private AlertDialog.Builder builder;
    private EditText urlText;

    private int codec_type = 0;
    private int checked_codec_type_item = -1;

    private ActionMode actionMode;
    private TextView deleteText;


    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private FileListActivityRecyclerAdapter mRecyclerViewAdapter;
    private List<MediaInfo> mediaInfoList;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        final SharedPreferences sp = getSharedPreferences("user", Context.MODE_PRIVATE);
        codec_type = sp.getInt("codec_type",0);

        Intent defauleIntent = getIntent();
        if(Intent.ACTION_VIEW.equals(defauleIntent.getAction()))
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(PERMISSION_WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{PERMISSION_WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUESTCODE);
                } else {
                    String default_url;
                    if(defauleIntent.getData() != null) {
                        default_url = defauleIntent.getData().getPath();
                    }
                    else {
                        default_url = defauleIntent.getType();
                    }
                    Log.i(TAG, "default_url: " + default_url);

                    if (".yuv".equals(default_url.substring(default_url.length() - 4).toLowerCase())) {
                        Intent intent = new Intent();
                        intent.setClass(this, OpenGLActivity.class);
                        intent.putExtra("input_url", default_url);
                        intent.putExtra("isDefault", true);
                        intent.putExtra("isYUV", true);
                        startActivity(intent);
                        finish();
                    } else {
                        Intent intent = new Intent();
                        intent.setClass(this, OpenGLActivity.class);
                        intent.putExtra("input_url", default_url);
                        intent.putExtra("isDefault", true);
                        intent.putExtra("codec_type", codec_type);
                        startActivity(intent);
                        finish();
                    }
                }
            }
            else{
                String default_url = defauleIntent.getData().getPath();
                Log.i(TAG, "default_url: " + default_url);

                if (".yuv".equals(default_url.substring(default_url.length() - 4).toLowerCase())) {
                    Intent intent = new Intent();
                    intent.setClass(this, OpenGLActivity.class);
                    intent.putExtra("input_url", default_url);
                    intent.putExtra("isDefault", true);
                    intent.putExtra("isYUV", true);
                    startActivity(intent);
                    finish();
                } else {
                    Intent intent = new Intent();
                    intent.setClass(this, OpenGLActivity.class);
                    intent.putExtra("input_url", default_url);
                    intent.putExtra("isDefault", true);
                    intent.putExtra("codec_type", codec_type);
                    startActivity(intent);
                    finish();
                }
            }
        }
        else {
            setContentView(R.layout.uvc_playback_activity_file_list);
            root = (RelativeLayout) findViewById(R.id.root);
            fab = (FloatingActionButton) findViewById(R.id.floatingActionButton);
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(actionMode!=null)
                        actionMode.finish();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (checkSelfPermission(PERMISSION_WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{PERMISSION_WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUESTCODE);
                        } else {
                            Intent getUrlintent = new Intent(Intent.ACTION_GET_CONTENT);
                            getUrlintent.addCategory(Intent.CATEGORY_OPENABLE);
                            getUrlintent.setType("video/*");
                            startActivityForResult(Intent.createChooser(getUrlintent, getResources().getText(R.string.uvc_select_file_manager)), GET_CONTENT_REQUESTCODE);
                        }
                    }
                    else{
                        Intent getUrlintent = new Intent(Intent.ACTION_GET_CONTENT);
                        getUrlintent.addCategory(Intent.CATEGORY_OPENABLE);
                        getUrlintent.setType("video/*");
                        startActivityForResult(Intent.createChooser(getUrlintent, getResources().getText(R.string.uvc_select_file_manager)), GET_CONTENT_REQUESTCODE);
                    }
                }
            });
            toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    switch (item.getItemId()) {
                        case R.id.action_stream_media:
                            showStreamMediaDialog();
                            break;
                        case R.id.action_codec_type:
                            showCodecTypeDialog();
                            break;
                    }
                    return true;
                }
            });

            mRecyclerView = (RecyclerView) findViewById(R.id.recyclerView);
            mLayoutManager = new LinearLayoutManager(this);
            mLayoutManager.setOrientation(OrientationHelper.VERTICAL);
            mRecyclerView.setLayoutManager(mLayoutManager);
            mRecyclerViewAdapter = new FileListActivityRecyclerAdapter(this);
            mRecyclerViewAdapter.setCreateViewLayout(R.layout.uvc_playback_item_file_list);

            mRecyclerViewAdapter.setOnItemClickListener(new OnItemClickListener(){
                @Override
                public void onClick(View view, int pos, String viewName) {
                    if ("itemView".equals(viewName)) {
                        if (actionMode != null) {// 如果当前处于多选状态，则进入多选状态的逻辑
                             addOrRemove(pos);
                        } else{//如果不是多选状态，则进入点击事件的业务逻辑
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                if (checkSelfPermission(PERMISSION_WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                    requestPermissions(new String[]{PERMISSION_WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUESTCODE);
                                } else {
                                    List<MediaInfo> mediaInfoList = mRecyclerViewAdapter.getDatas("mediaInfo");
                                    String input_url = mediaInfoList.get(pos).getUrl();
                                    long now = new Date().getTime();
                                    String type = mediaInfoList.get(pos).getType();
                                    String thumbnailPath = mediaInfoList.get(pos).getThumbnailPath();

                                    if(!ConstantUtils.isStreamMedia(type) && !new File(input_url).exists()) {
                                        showToast(input_url+getResources().getText(R.string.uvc_file_unexist).toString(), Toast.LENGTH_SHORT);
                                    } else{
                                        if ("yuv".equals(type)) {
                                            Intent intent = new Intent();
                                            intent.setClass(FileListActivity.this, OpenGLActivity.class);
                                            intent.putExtra("input_url", input_url);
                                            intent.putExtra("isYUV", true);
                                            startActivityForResult(intent,REFRESH_UI);
                                        } else {
                                            if (ConstantUtils.isStreamMedia(type)) {
                                                Intent intent = new Intent();
                                                intent.setClass(FileListActivity.this, OpenGLActivity.class);
                                                intent.putExtra("input_url", input_url);
                                                intent.putExtra("isStreamMedia", true);
                                                intent.putExtra("codec_type", codec_type);
                                                startActivity(intent);
                                            } else {
                                                Intent intent = new Intent();
                                                intent.setClass(FileListActivity.this, OpenGLActivity.class);
                                                intent.putExtra("input_url", input_url);
                                                intent.putExtra("codec_type", codec_type);
                                                startActivity(intent);
                                            }
                                        }
//                                        UrlService urlService = new UrlService();
//                                        urlService.addOrUpdateTimeOrType(input_url, now, type);
                                        MediaInfo mediaInfo = mediaInfoList.remove(pos);
                                        mediaInfo.setTime(now);
                                        mediaInfo.setType(type);
                                        mediaInfoList.add(0,mediaInfo);
                                        mRecyclerViewAdapter.notifyDataSetChanged();
                                    }
                                }
                            }
                            else{
                                List<MediaInfo> mediaInfoList = mRecyclerViewAdapter.getDatas("mediaInfo");
                                String input_url = mediaInfoList.get(pos).getUrl();
                                long now = new Date().getTime();
                                String type = mediaInfoList.get(pos).getType();
                                String thumbnailPath = mediaInfoList.get(pos).getThumbnailPath();

                                if(!ConstantUtils.isStreamMedia(type) && !new File(input_url).exists()) {
                                    showToast(input_url+getResources().getText(R.string.uvc_file_unexist).toString(), Toast.LENGTH_SHORT);
                                } else{
                                    if ("yuv".equals(type)) {
                                        Intent intent = new Intent();
                                        intent.setClass(FileListActivity.this, OpenGLActivity.class);
                                        intent.putExtra("input_url", input_url);
                                        intent.putExtra("isYUV", true);
                                        startActivityForResult(intent,REFRESH_UI);
                                    } else {
                                        if (ConstantUtils.isStreamMedia(type)) {
                                            Intent intent = new Intent();
                                            intent.setClass(FileListActivity.this, OpenGLActivity.class);
                                            intent.putExtra("input_url", input_url);
                                            intent.putExtra("isStreamMedia", true);
                                            intent.putExtra("codec_type", codec_type);
                                            startActivity(intent);
                                        } else {
                                            Intent intent = new Intent();
                                            intent.setClass(FileListActivity.this, OpenGLActivity.class);
                                            intent.putExtra("input_url", input_url);
                                            intent.putExtra("codec_type", codec_type);
                                            startActivity(intent);
                                        }
                                    }
//                                    UrlService urlService = new UrlService();
//                                    urlService.addOrUpdateTimeOrType(input_url, now, type);
                                    MediaInfo mediaInfo = mediaInfoList.remove(pos);
                                    mediaInfo.setTime(now);
                                    mediaInfo.setType(type);
                                    mediaInfoList.add(0,mediaInfo);
                                    mRecyclerViewAdapter.notifyDataSetChanged();
                                }
                            }
                        }
                    }
                }
            });
            mRecyclerViewAdapter.setOnItemLongClickListener(new OnItemLongClickListener() {
                @Override
                public void onLongClick(View view, int pos, String viewName) {
                    if ("itemView".equals(viewName)) {
                        if (actionMode == null) {
                            actionMode = startSupportActionMode(FileListActivity.this);
                            addOrRemove(pos);
                        }
                    }
                }
            });

            initListViewData();
            mRecyclerView.setAdapter(mRecyclerViewAdapter);
        }
    }

    private void addOrRemove(int position) {
        if(mRecyclerViewAdapter.isSelectAll){
            for(int i = 0;i < mRecyclerViewAdapter.getItemCount();i++)
                mRecyclerViewAdapter.positionSet.add(i);
            mRecyclerViewAdapter.isSelectAll = false;
        }
        if (mRecyclerViewAdapter.positionSet.contains(position)) {
            // 如果包含，则撤销选择
            mRecyclerViewAdapter.positionSet.remove(position);
        } else {
            // 如果不包含，则添加
            mRecyclerViewAdapter.positionSet.add(position);
        }
        if (mRecyclerViewAdapter.positionSet.size() == 0) {
            // 如果没有选中任何的item，则退出多选模式
            actionMode.finish();
        } else {
            // 设置ActionMode标题
            actionMode.setTitle(getResources().getText(R.string.uvc_actionbar_long_click_check).toString()
                    + mRecyclerViewAdapter.positionSet.size() + getResources().getText(R.string.uvc_actionbar_long_click_check_unit).toString());
            // 更新列表界面，否则无法显示已选的item
            mRecyclerViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        if (actionMode == null) {
            actionMode = mode;
            getMenuInflater().inflate(R.menu.uvc_menu_main_long_click, menu);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete:
                // 删除已选
                showDeleteDialog(mode);
                return true;
            case R.id.action_select_all:
                if(!mRecyclerViewAdapter.isSelectAll) {
                    mRecyclerViewAdapter.isSelectAll = true;
                    actionMode.setTitle(getResources().getText(R.string.uvc_actionbar_long_click_all_check).toString()
                            + mRecyclerViewAdapter.positionSet.size() + getResources().getText(R.string.uvc_actionbar_long_click_all_check_unit).toString());
                    mRecyclerViewAdapter.notifyDataSetChanged();
                }
                else{
                    mRecyclerViewAdapter.isSelectAll = false;
                    actionMode.setTitle(getResources().getText(R.string.uvc_actionbar_long_click_check).toString()
                            + mRecyclerViewAdapter.positionSet.size() + getResources().getText(R.string.uvc_actionbar_long_click_check_unit).toString());
                    mRecyclerViewAdapter.notifyDataSetChanged();
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        actionMode = null;
        mRecyclerViewAdapter.isSelectAll = false;
        mRecyclerViewAdapter.positionSet.clear();
        mRecyclerViewAdapter.notifyDataSetChanged();
    }

    void initListViewData(){
//        UrlService urlService = new UrlService();
//        mediaInfoList = urlService.getMediaInfoList();
        mediaInfoList = new ArrayList<>();
        File fileDirectory = new File(LocalUVCPreviewActivity.DEFAILT_LOCAL_UVC);
        if(fileDirectory.exists() && fileDirectory.isDirectory()) {
            File[] fileList = fileDirectory.listFiles();
            for(int i = 0; i < fileList.length; i++){
                String input_url = fileList[i].getPath();
                String type = input_url.substring(input_url.lastIndexOf('.')+1).toLowerCase();
                long now = fileList[i].lastModified();;
//                try {
//                    type = input_url.substring(input_url.lastIndexOf('.')+1).toLowerCase();
//                    String name = input_url.substring(input_url.lastIndexOf(File.separator)+1 ,input_url.lastIndexOf('.'));
//                    now = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").parse(name).getTime();
//                } catch (ParseException e) {
//                    e.printStackTrace();
//                    now = new Date().getTime();
//                } catch (Exception e){
//                    Log.i(TAG, "initListViewData: Exception input_url = "+input_url);
//                    continue;
//                }
                if("mp4".equals(type)) {
                    MediaInfo mediaInfo = new MediaInfo();
                    mediaInfo.setUrl(input_url);
                    mediaInfo.setTime(now);
                    mediaInfo.setType(type);
                    mediaInfoList.add(mediaInfo);
                }
            }
        }
        mRecyclerViewAdapter.addDatas("mediaInfo", mediaInfoList);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.uvc_menu_main_normal,menu);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUESTCODE:
                if ("android.permission.WRITE_EXTERNAL_STORAGE".equals(permissions[0])
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("video/*");
                    startActivityForResult(Intent.createChooser(intent,getResources().getText(R.string.uvc_select_file_manager)),GET_CONTENT_REQUESTCODE);
                }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case GET_CONTENT_REQUESTCODE:
                    Uri uri = data.getData();
                    String input_url = uri.getPath();
                    if("/root".equals(input_url.substring(0,5))){
                        input_url = input_url.substring(5);
                    }

                    long now = new File(input_url).lastModified();
//                    UrlService urlService = new UrlService();
                    String type = input_url.substring(input_url.lastIndexOf(".")+1).toLowerCase();

                    if("yuv".equals(type)) {
                        Intent intent = new Intent();
                        intent.setClass(this, OpenGLActivity.class);
                        intent.putExtra("input_url", input_url);
                        intent.putExtra("isYUV",true);
                        startActivityForResult(intent,REFRESH_UI);
                    }
                    else{
                        Log.i(TAG, "############input_url: "+input_url);
                        Intent intent = new Intent();
                        intent.setClass(this, OpenGLActivity.class);
                        intent.putExtra("input_url", input_url);
                        intent.putExtra("codec_type", codec_type);
                        startActivity(intent);
                    }

                    int index = -1;
                    for(int i = 0; i < mediaInfoList.size(); i++){
                        if(input_url.equals(mediaInfoList.get(i).getUrl())){
                            index = i;
                        }
                    }

                    if(index == -1) {
                        MediaInfo mediaInfo = new MediaInfo();
                        mediaInfo.setUrl(input_url);
                        mediaInfo.setTime(now);
                        mediaInfo.setType(type);
                        mediaInfoList.add(0,mediaInfo);
                    }
                    else{
                        MediaInfo mediaInfo = mediaInfoList.remove(index);
                        mediaInfo.setTime(now);
                        mediaInfo.setType(type);
                        mediaInfoList.add(0,mediaInfo);
                    }
                    mRecyclerViewAdapter.notifyDataSetChanged();
                    break;
                case REFRESH_UI:
                    initListViewData();
                    mRecyclerViewAdapter.notifyDataSetChanged();
                    break;
            }
        }
    }

    private void showStreamMediaDialog(){
        builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.uvc_dialog_stream_media);
        LayoutInflater layoutInflater = getLayoutInflater();
        View dialogView = layoutInflater.inflate(R.layout.uvc_playback_dialog_stream_media,null);
        urlText = (EditText) dialogView.findViewById(R.id.urlText);
        final SharedPreferences sp = getSharedPreferences("user", Context.MODE_PRIVATE);
        urlText.setText(sp.getString("stream_media_url",""));
        urlText.setSelection(urlText.getText().toString().length());
        builder.setView(dialogView);
        builder.setPositiveButton(R.string.uvc_dialog_stream_media_ok, null);
        builder.setNegativeButton(R.string.uvc_dialog_stream_media_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        final AlertDialog dialog = builder.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.customBlue));
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.textSecondaryColor));
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String input_url = urlText.getText().toString();

                long now = new Date().getTime();
//                UrlService urlService = new UrlService();

                String url_header;
                try {
                    url_header = input_url.substring(0, input_url.indexOf("://")).toLowerCase();
                    Log.i(TAG, "stream_media_url_header: "+url_header);
                    if(ConstantUtils.isStreamMedia(url_header)) {
                        SharedPreferences.Editor editor = sp.edit();
                        editor.putString("stream_media_url",input_url);
                        editor.commit();

                        int index = -1;
                        for(int i = 0; i < mediaInfoList.size(); i++){
                            if(input_url.equals(mediaInfoList.get(i).getUrl())){
                                index = i;
                            }
                        }

                        if(index == -1) {
                            MediaInfo mediaInfo = new MediaInfo();
                            mediaInfo.setUrl(input_url);
                            mediaInfo.setTime(now);
                            mediaInfo.setType(url_header);
                            mediaInfoList.add(0,mediaInfo);
                        } else {
                            MediaInfo mediaInfo = mediaInfoList.remove(index);
                            mediaInfo.setTime(now);
                            mediaInfo.setType(url_header);
                            mediaInfoList.add(0,mediaInfo);
                        }
                        mRecyclerViewAdapter.notifyDataSetChanged();

                        Intent intent = new Intent();
                        intent.setClass(FileListActivity.this, OpenGLActivity.class);
                        intent.putExtra("input_url", input_url);
                        intent.putExtra("isStreamMedia", true);
                        intent.putExtra("codec_type", codec_type);
                        startActivity(intent);
                        dialog.dismiss();
                    }
                    else{
                        showToast(getResources().getText(R.string.uvc_dialog_stream_media_error).toString(), Toast.LENGTH_SHORT);
                        urlText.setText("");
                        urlText.setSelection(urlText.getText().toString().length());
                    }
                }
                catch (Exception e){
                    e.printStackTrace();
                    showToast(getResources().getText(R.string.uvc_dialog_stream_media_error).toString(), Toast.LENGTH_SHORT);
                    urlText.setText("");
                    urlText.setSelection(urlText.getText().toString().length());
                }
            }
        });
    }

    private void showCodecTypeDialog(){
        checked_codec_type_item = -1;
        final SharedPreferences sp = getSharedPreferences("user", Context.MODE_PRIVATE);
        Log.i(TAG, "showCodecTypeDialog: codec_type = "+codec_type);

        builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.uvc_dialog_codec_type);

        final String[] codec_type_items = new String[]{getResources().getText(R.string.uvc_dialog_codec_type_soft).toString(),
                                                        getResources().getText(R.string.uvc_dialog_codec_type_hard).toString()};

        builder.setSingleChoiceItems(codec_type_items, codec_type, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                checked_codec_type_item = which;
                Log.i(TAG, "showCodecTypeDialog: checked_codec_type_item = "+checked_codec_type_item);
            }
        });

        builder.setPositiveButton(R.string.uvc_dialog_codec_type_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if(checked_codec_type_item != -1){
                    codec_type = checked_codec_type_item;
                    SharedPreferences.Editor editor = sp.edit();
                    editor.putInt("codec_type",codec_type);
                    editor.commit();
                    Log.i(TAG, "showCodecTypeDialog: codec_type commit = "+codec_type);
                }
            }
        });
        builder.setNegativeButton(R.string.uvc_dialog_codec_type_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.customBlue));
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.textSecondaryColor));
    }

    private void showDeleteDialog(final ActionMode mode){
        builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.uvc_dialog_delete);
        LayoutInflater layoutInflater = getLayoutInflater();
        View dialogView = layoutInflater.inflate(R.layout.uvc_playback_dialog_delete,null);
        deleteText = (TextView) dialogView.findViewById(R.id.deleteText);
        String text = "";
        if(mRecyclerViewAdapter.isSelectAll){
            for(int i = 0;i < mediaInfoList.size();i++) {
                if (ConstantUtils.isStreamMedia(mediaInfoList.get(i).getType())) {
                    text = text + mediaInfoList.get(i).getUrl() + "\n";
                }else{
                    text = text + mediaInfoList.get(i).getUrl().substring(mediaInfoList.get(i).getUrl().lastIndexOf("/") + 1) + "\n";
                }
            }
        }
        else {
            Iterator iterator = mRecyclerViewAdapter.positionSet.iterator();
            while (iterator.hasNext()) {
                int pos = (int) iterator.next();
                String name;
                if (ConstantUtils.isStreamMedia(mediaInfoList.get(pos).getType())) {
                    name = mediaInfoList.get(pos).getUrl();
                } else {
                    name = mediaInfoList.get(pos).getUrl().substring(mediaInfoList.get(pos).getUrl().lastIndexOf("/") + 1);
                }
                text = text + name + "\n";
            }
        }
        deleteText.setText(text);
        builder.setView(dialogView);
        builder.setPositiveButton(R.string.uvc_dialog_delete_delete, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
//                UrlService urlService = new UrlService();
                if(mRecyclerViewAdapter.isSelectAll){
                    for(int i = 0;i < mediaInfoList.size();i++) {
                        mediaInfoList.set(i,null);
//                        urlService.removeUrl(mediaInfoList.get(i).getUrl());
                    }
                }
                else {
                    Iterator iterator = mRecyclerViewAdapter.positionSet.iterator();
                    while (iterator.hasNext()) {
                        int pos = (int) iterator.next();
                        mediaInfoList.set(pos,null);
//                        urlService.removeUrl(mediaInfoList.get(pos).getUrl());
                    }
                }
                int deleteIndex = mediaInfoList.indexOf(null);
                while (deleteIndex != -1) {
                    mediaInfoList.remove(deleteIndex);
                    deleteIndex = mediaInfoList.indexOf(null);
                }
                mode.finish();
            }
        });
        builder.setNegativeButton(R.string.uvc_dialog_delete_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.setNeutralButton(R.string.uvc_dialog_delete_delete_origin,new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
//                UrlService urlService = new UrlService();
                if(mRecyclerViewAdapter.isSelectAll){
                    for(int i = 0;i < mediaInfoList.size();i++) {
                        if(!ConstantUtils.isStreamMedia(mediaInfoList.get(i).getType())){
                            File file = new File(mediaInfoList.get(i).getUrl());
                            file.delete();
                        }
                        mediaInfoList.set(i,null);
//                        urlService.removeUrl(mediaInfoList.get(i).getUrl());
                    }
                }
                else {
                    Iterator iterator = mRecyclerViewAdapter.positionSet.iterator();
                    while (iterator.hasNext()) {
                        int pos = (int) iterator.next();
                        if(!ConstantUtils.isStreamMedia(mediaInfoList.get(pos).getType())){
                            File file = new File(mediaInfoList.get(pos).getUrl());
                            file.delete();
                        }
                        mediaInfoList.set(pos,null);
//                        urlService.removeUrl(mediaInfoList.get(pos).getUrl());
                    }
                }
                int deleteIndex = mediaInfoList.indexOf(null);
                while (deleteIndex != -1) {
                    mediaInfoList.remove(deleteIndex);
                    deleteIndex = mediaInfoList.indexOf(null);
                }
                mode.finish();
            }
        });
        AlertDialog dialog = builder.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.customBlue));
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.textSecondaryColor));
    }

    /**
     * 显示Toast，解决重复弹出问题
     */
    public void showToast(String text , int time) {
        if(mToast == null) {
            mToast = Toast.makeText(this, text, time);
            mToast.setText(text);
        } else {
            mToast.setText(text);
            mToast.setDuration(Toast.LENGTH_SHORT);
        }
        mToast.show();
    }

    /**
     * 隐藏Toast
     */
    public void cancelToast() {
        if (mToast != null) {
            mToast.cancel();
        }
    }

    public void onBackPressed() {
        cancelToast();
        finish();
    }
}

class FileListActivityRecyclerAdapter extends BaseRecyclerViewAdapter {
    private static final String TAG = "MAIN";
    private Context mContext;
    public Set<Integer> positionSet;
    public boolean isSelectAll = false;

    public FileListActivityRecyclerAdapter(Context context) {
        super(context);
        mContext = context;
        positionSet = new TreeSet<Integer>();
    }

    @Override
    public int getItemCount() {
        if (mHeaderView == null && mFooterView == null) return mDatas.get("mediaInfo").size();
        if (mHeaderView != null && mFooterView != null) return mDatas.get("mediaInfo").size() + 2;
        return mDatas.get("mediaInfo").size() + 1;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if(viewType == TYPE_HEADER) return new Holder(mHeaderView, viewType);
        if(viewType == TYPE_FOOTER) return new Holder(mFooterView, viewType);
        View layout = mInflater.inflate(mCreateViewLayout, parent, false);
        return new Holder(layout, viewType);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        Holder holder = (Holder)viewHolder;
        final int pos = getRealPosition(holder);
        if(getItemViewType(position) == TYPE_HEADER || getItemViewType(position) == TYPE_FOOTER) return;
        if(getItemViewType(position) == TYPE_NORMAL) {
            List<MediaInfo> mediaInfoList = mDatas.get("mediaInfo");
            String url = mediaInfoList.get(pos).getUrl();
            Long time = mediaInfoList.get(pos).getTime();
            String type = mediaInfoList.get(pos).getType();
            String thumbnailPath = LocalUVCPreviewActivity.DEFAILT_LOCAL_UVC+"thumbnail" +File.separator +url.substring(url.lastIndexOf(File.separator)+1,url.lastIndexOf('.'))+".jpg";
            holder.time.setText(new SimpleDateFormat("yy/MM/dd HH:mm:ss").format(new Date(time)));
            holder.label.setCardBackgroundColor(mContext.getResources().getColor(ConstantUtils.getColorByType(type)));
            holder.type.setText(type.toUpperCase());
            holder.type_copy.setText(type.toUpperCase());
            if (!ConstantUtils.isStreamMedia(type)) {
                holder.url.setText(url.substring(url.lastIndexOf(File.separator) + 1));
                File file = new File(url);
                holder.length.setText(FileUtils.getDataSize(file.length()));
            } else {
                holder.url.setText(url.substring(url.indexOf("://") + 3));
                holder.length.setText("");
            }

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mOnItemClickListener.onClick(v, pos, "itemView");
                }
            });
            holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    mOnItemLongClickListener.onLongClick(v, pos, "itemView");
                    return true;
                }
            });

            if(!"yuv".equals(type)) {
                if (!new File(thumbnailPath).exists()) {
                    holder.img.setVisibility(View.INVISIBLE);
                    if (!mediaInfoList.get(pos).isDownLoadThumbnailTask()) {
                        mediaInfoList.get(pos).setDownLoadThumbnailTask(true);
                        new DownLoadThumbnailTask().execute(mediaInfoList.get(pos));
                    }
                } else {
                    Log.i(TAG, "onBindViewHolder: "+thumbnailPath);
                    Glide.with(mContext).load(thumbnailPath).into(holder.thumbnail);
                    holder.img.setVisibility(View.VISIBLE);
                }
            }
            else{
                holder.img.setVisibility(View.INVISIBLE);
            }

            if (isSelectAll) {
                holder.itemView.setBackgroundColor(mContext.getResources().getColor(R.color.lighter_gray));
            } else {
                if (positionSet.contains(pos)) {
                    holder.itemView.setBackgroundColor(mContext.getResources().getColor(R.color.lighter_gray));
                } else {
                    int[] attrsArray = {android.R.attr.selectableItemBackground};
                    TypedArray typedArray = mContext.obtainStyledAttributes(attrsArray);
                    int colorResID = typedArray.getResourceId(0, -1);
                    typedArray.recycle();
                    holder.itemView.setBackgroundResource(colorResID);
                }
            }
        }
    }

    class DownLoadThumbnailTask extends AsyncTask<MediaInfo,Integer,MediaInfo> {
        @Override
        protected MediaInfo doInBackground(MediaInfo... mediaInfos) {
            String url = mediaInfos[0].getUrl();
            if(url == null){
                return null;
            }
            if(!ConstantUtils.isStreamMedia(mediaInfos[0].getType())) {
                if (!new File(url).exists()) {
                    return null;
                }
                if (new File(url).length() == 0) {
                    return null;
                }
            }
            Bitmap thumbnail;
            MediaMetadataRetriever ffmr = new MediaMetadataRetriever();
            try {
                ffmr.setDataSource(url);
                String width = ffmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String height = ffmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                String rotate = ffmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                if(width != null) {
                    mediaInfos[0].setWidth(Integer.parseInt(width));
                }
                if(height != null) {
                    mediaInfos[0].setHeight(Integer.parseInt(height));
                }
                if(rotate != null) {
                    mediaInfos[0].setRotate(Integer.parseInt(rotate));
                }

                Log.i(TAG, "doInBackground: "+ mediaInfos[0].toString());
                thumbnail = ffmr.getFrameAtTime();
                if(thumbnail != null){
//                    thumbnail = ImageUtils.rotateBitmap(thumbnail, Float.parseFloat(rotate));
                    String name = url.substring(url.lastIndexOf(File.separator)+1,url.lastIndexOf('.'));
                    String savePath = ImageUtils.saveBitmap(mContext,thumbnail,LocalUVCPreviewActivity.DEFAILT_LOCAL_UVC+"thumbnail",name);
                    if(savePath != null) {
                        mediaInfos[0].setThumbnailPath(savePath);
//                        UrlService urlService = new UrlService();
//                        urlService.updateThumbnailPath(mediaInfos[0].getUrl(), mediaInfos[0].getThumbnailPath());
                    }
                    thumbnail.recycle();
                }
            }
            catch (NumberFormatException e){
                e.printStackTrace();
            }
            catch (IllegalArgumentException e){
                e.printStackTrace();
            }
            finally {
                ffmr.release();
            }
            return mediaInfos[0];
        }

        @Override
        protected void onPostExecute(MediaInfo mediaInfo) {
            super.onPostExecute(mediaInfo);
            if(mediaInfo != null){
                if(mediaInfo.getThumbnailPath() != null) {
                    Log.i(TAG, "onPostExecute: ThumbnailPath = " + mediaInfo.getThumbnailPath());
                    List<MediaInfo> mediaInfoList = mDatas.get("mediaInfo");
                    int pos = -1;
                    for (int i = 0; i < mediaInfoList.size(); i++) {
                        if (mediaInfoList.get(i).getUrl().equals(mediaInfo.getUrl())) {
                            pos = i;
                        }
                    }
                    Log.i(TAG, "onPostExecute: MediaInfo pos = " + pos);
                    if (pos >= 0 && pos < mDatas.get("mediaInfo").size()) {
                        notifyItemChanged(pos);
                        Log.i(TAG, "onPostExecute: MediaInfo pos(" + pos + ") notifyItemChanged");
                    }
                }
                mediaInfo.setDownLoadThumbnailTask(false);
            }
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        public TextView url;
        public TextView time;
        public TextView length;
        public CardView label;
        public TextView type;
        public CardView img;
        public ImageView thumbnail;
        public TextView type_copy;
        public Holder(View itemView, int viewType) {
            super(itemView);
            if(viewType == TYPE_HEADER || viewType == TYPE_FOOTER) return;
            if(viewType == TYPE_NORMAL) {
                url = (TextView) itemView.findViewById(R.id.url);
                time = (TextView) itemView.findViewById(R.id.time);
                length = (TextView) itemView.findViewById(R.id.length);
                label = (CardView) itemView.findViewById(R.id.label);
                type = (TextView) itemView.findViewById(R.id.type);
                img = (CardView) itemView.findViewById(R.id.img);
                thumbnail = (ImageView) itemView.findViewById(R.id.thumbnail);
                type_copy = (TextView) itemView.findViewById(R.id.type_copy);
            }
        }
    }
}
