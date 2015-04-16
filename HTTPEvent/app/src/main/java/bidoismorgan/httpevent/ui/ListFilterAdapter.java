package bidoismorgan.httpevent.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.ArrayList;

import bidoismorgan.httpevent.Constants;
import bidoismorgan.httpevent.R;

/**
 * Created by Bidois Morgan on 15/04/15.
 */
public class ListFilterAdapter extends BaseAdapter implements ListAdapter {

    /**
     * List of filter added by the user
     */
    private ArrayList<String> listFilters = new ArrayList<String>();
    private Context context;

    public ListFilterAdapter(ArrayList<String> list, Context context) {
        this.context = context;
        this.listFilters = list;
    }

    public ListFilterAdapter(Context context) {
        this.context = context;
        this.listFilters = new ArrayList<String>();
    }

    @Override
    public int getCount() {
        return listFilters.size();
    }

    @Override
    public Object getItem(int position) {
        return listFilters.get(position);
    }

    @Override
    public long getItemId(int position) {
        // No id
        return 0;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        View view = convertView;

        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.list_layout_filter, null);
        }

        //Handle TextView and display string from the list
        EditText listItemText = (EditText) view.findViewById(R.id.list_item_string);
        listItemText.setText(listFilters.get(position));

        listItemText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Log.v(Constants.LOG_TAG, "Maj text");
//                String oldFilter = listFilters.get(position);
//                oldFilter = s.toString();
//                notifyDataSetChanged();

                listFilters.remove(position);
                listFilters.add(s.toString());
                notifyDataSetChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        //Handle buttons and add onClickListeners
        Button deleteBtn = (Button) view.findViewById(R.id.delete_btn);

        deleteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listFilters.remove(position);
                notifyDataSetChanged();
            }
        });

        return view;
    }

    public boolean add(String newFilter){
        boolean addOK = this.listFilters.add("");
        notifyDataSetChanged();
        return addOK;
    }
}
