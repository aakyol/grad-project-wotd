package com.wotd_app.WOTD;

//An ArrayAdapter class to implement spinner expand listener methods, to provide text padding dynamically, which provides better readability

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collection;

public class ListeningArrayAdapter<T> extends ArrayAdapter<T> { 
    private ViewGroup itemParent;
    private final Collection<SpinnerListener> spinnerListeners = new ArrayList<SpinnerListener>();

    public ListeningArrayAdapter(Context context, int resource, T[] objects) {
        super(context, resource, objects);
    }

    // Add the rest of the constructors here ...


    // Just grab the spinner view (parent of the spinner item view) and add a listener to it.
    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        if (isParentTheListView(parent)) {
            itemParent = parent;
            addFocusListenerAsExpansionListener();
        }

        return super.getDropDownView(position, convertView, parent);
    }

    // Assumes the item view parent is a ListView (which it is when a Spinner class is used)
    private boolean isParentTheListView(ViewGroup parent) {
        return (parent != itemParent && parent != null && ListView.class.isAssignableFrom(parent.getClass()));
    }

    // Add a focus listener to listen to spinner expansion and collapse events.
    private void addFocusListenerAsExpansionListener() {
        final View.OnFocusChangeListener listenerWrapper = new OnFocusChangeListenerWrapper(itemParent.getOnFocusChangeListener(), spinnerListeners);
        itemParent.setOnFocusChangeListener(listenerWrapper);
    }

    // Utility method.
    public boolean isExpanded() {
        return (itemParent != null && itemParent.hasFocus());
    }

    public void addSpinnerListener(SpinnerListener spinnerListener) {
        spinnerListeners.add(spinnerListener);
    }

    public boolean removeSpinnerListener(SpinnerListener spinnerListener) {
        return spinnerListeners.remove(spinnerListener);
    }

    // Listener that listens for 'expand' and 'collapse' events.
    private static class OnFocusChangeListenerWrapper implements View.OnFocusChangeListener {
        private final Collection<SpinnerListener> spinnerListeners;
        private final View.OnFocusChangeListener originalFocusListener;

        private OnFocusChangeListenerWrapper(View.OnFocusChangeListener originalFocusListener, Collection<SpinnerListener> spinnerListeners) {
            this.spinnerListeners = spinnerListeners;
            this.originalFocusListener = originalFocusListener;
        }

        @Override
        public void onFocusChange(View view, boolean hasFocus) {
            if (originalFocusListener != null) {
                originalFocusListener.onFocusChange(view, hasFocus); // Preserve the pre-existing focus listener (if any).
            }

            callSpinnerListeners(hasFocus);
        }

        private void callSpinnerListeners(boolean hasFocus) {
            for (SpinnerListener spinnerListener : spinnerListeners) {
                if (spinnerListener != null) {
                    callSpinnerListener(hasFocus, spinnerListener);
                }
            }
        }

        private void callSpinnerListener(boolean hasFocus, SpinnerListener spinnerListener) {
            if (hasFocus) {
                spinnerListener.onSpinnerExpanded();
            }
            else {
                spinnerListener.onSpinnerCollapsed();
            }
        }
    }
}
