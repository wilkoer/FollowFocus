package ath.lmu.de.followfocus;

import android.content.Context;
import android.transition.Scene;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by alexander on 21.08.15.
 */
public class SceneListAdapter extends BaseAdapter {
    Context mContext;
    ArrayList<FocusScene> scenes = new ArrayList<>();


    public SceneListAdapter(Context c) {
        mContext = c;
    }

    public void add(FocusScene scene) {
        scenes.add(scene);
    }

    @Override
    public int getCount() {
        return scenes.size();
    }

    @Override
    public Object getItem(int position) {
        return scenes.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = null;
        TextView sceneName = new TextView(mContext);
        TextView sceneLength = new TextView(mContext);

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            view = inflater.inflate(R.layout.recording_list_entry, null);

        } else {
            view = convertView;
        }

        sceneName = (TextView) view.findViewById(R.id.textView_recording_name);
        sceneLength = (TextView) view.findViewById(R.id.textView_recording_length);

        sceneName.setText(scenes.get(position).getName());
        sceneLength.setText(scenes.get(position).getStatus());



        return view;
    }
}
