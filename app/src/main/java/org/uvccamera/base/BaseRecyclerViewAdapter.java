package org.uvccamera.base;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by huxin on 2017/2/27.
 */

public class BaseRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_FOOTER = 1;
    public static final int TYPE_NORMAL = 2;
    protected Context mContent;
    protected LayoutInflater mInflater;
    protected Map<String, List> mDatas;
    protected View mHeaderView;
    protected View mFooterView;
    protected int mCreateViewLayout;
    protected OnItemClickListener mOnItemClickListener;
    protected OnItemLongClickListener mOnItemLongClickListener;

    public BaseRecyclerViewAdapter(Context context){
        this.mInflater=LayoutInflater.from(context);
        this.mContent = context;
        this.mDatas = new HashMap<String, List>();
    }

    public void setHeaderView(View mHeaderView) {
        this.mHeaderView = mHeaderView;
        notifyItemInserted(0);//将新控件插入到第0个后面
    }

    public void setFooterView(View mHeaderView) {
        this.mFooterView = mHeaderView;
        notifyItemInserted(getItemCount() - 1);
    }

    public void addDatas(String key, List data) {
        mDatas.put(key, data);
        notifyDataSetChanged(); //刷新整个界面
    }

    public List getDatas(String key) {
        return mDatas.get(key);
    }

    public void setCreateViewLayout(int mCreateViewLayout) {
        this.mCreateViewLayout = mCreateViewLayout;
    }

    @Override
    public int getItemViewType(int position) {
        if(mHeaderView == null && mFooterView == null) return TYPE_NORMAL;
        if(mHeaderView != null && position == 0) return TYPE_HEADER;
        if(mFooterView != null && position == getItemCount() - 1) return TYPE_FOOTER;
        return TYPE_NORMAL;
    }

    /**
     * 绑定item布局
     * @param parent 父布局
     * @param viewType 绑定item的类型
     */
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if(mHeaderView != null && viewType == TYPE_HEADER) return new Holder(mHeaderView, viewType);
        if(mFooterView != null && viewType == TYPE_FOOTER) return new Holder(mFooterView, viewType);
        View layout = mInflater.inflate(mCreateViewLayout, parent, false);
        return new Holder(layout, viewType);
    }

    /**
     * item数据的显示
     * @param viewHolder
     * @param position item在列表中的位置，从0开始，到getItemCount()-1
     */
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        Holder holder = (Holder)viewHolder;
        final int pos = getRealPosition(holder);
        if(getItemViewType(position) == TYPE_HEADER || getItemViewType(position) == TYPE_FOOTER) return;
        if(getItemViewType(position) == TYPE_NORMAL) {

        }
    }

    /**
     * 绑定item布局中的控件
     * 自定义的ViewHolder，持有每个item的的所有界面元素
     */
    static class Holder extends RecyclerView.ViewHolder {
        public Holder(View itemView, int viewType) {
            super(itemView);
            if(viewType == TYPE_HEADER || viewType == TYPE_FOOTER) return;
            if(viewType == TYPE_NORMAL) {

            }
        }
    }

    /**
     * item在数据数组中的真实位置，
     * TYPE_HEADER、TYPE_FOOTER中不可用
     */
    protected int getRealPosition(RecyclerView.ViewHolder holder) {
        int position = holder.getLayoutPosition();
        return mHeaderView == null ? position : position - 1;
    }

    /**
     * return 列表中item总个数，包括TYPE_HEADER、TYPE_FOOTER
     */
    @Override
    public int getItemCount() {
        if(mHeaderView == null && mFooterView == null) return  mDatas.get("").size();
        if(mHeaderView != null && mFooterView != null) return  mDatas.get("").size()+2;
        return mDatas.get("").size()+1;
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener ){
        this.mOnItemClickListener = onItemClickListener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener onItemLongClickListener ){
        this.mOnItemLongClickListener = onItemLongClickListener;
    }

    //添加数据
    public void addItem(int position, Object data) {
        mDatas.get("").add(position, data);
        notifyItemInserted(position);
    }
    //删除数据
    public Object removeItem(int position) {
        Object obj = mDatas.remove(position);
        notifyItemRemoved(position);
        return obj;
    }
}
