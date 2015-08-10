/*
 * Copyright (C) 2015 NuBits Developers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.matthewmitchell.peercoin_android_wallet.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcelable;

import com.matthewmitchell.peercoin_android_wallet.R;
import com.matthewmitchell.peercoin_android_wallet.ui.preference.TrustedServer;

/**
 *
 * @author Matthew Mitchell
 */
public class ConfirmationDialogFragment extends DialogFragment {
	
	private String title, message;
	private Parcelable object;
	
	private static final String OBJECT_INSTANCE = "object";
	private static final String TITLE_INSTANCE = "title";
	private static final String MESSAGE_INSTANCE = "message";
	
	public interface ConfirmationDialogCallbacks {
		public void onNegative(Parcelable object);
		public void onPositive(Parcelable object);
	} 
	
	public static void create(String title, String message, Fragment parent, Parcelable object) {
		
		final  ConfirmationDialogFragment instance = new ConfirmationDialogFragment();
		
		instance.title = title;
		instance.message = message;
		instance.object = object;
		
		instance.setTargetFragment(parent, 0);
		instance.show(parent.getFragmentManager(), "dialog");
		
	}
	
	@Override
	public void onSaveInstanceState (Bundle outState) {
		
		super.onSaveInstanceState(outState);
		outState.putParcelable(OBJECT_INSTANCE, object);
		outState.putString(TITLE_INSTANCE, title);
		outState.putString(MESSAGE_INSTANCE, message);
		
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		
		if (savedInstanceState != null) {
			object = savedInstanceState.getParcelable(OBJECT_INSTANCE);
			title = savedInstanceState.getString(TITLE_INSTANCE);
			message = savedInstanceState.getString(MESSAGE_INSTANCE);
		}
		
		return new AlertDialog.Builder(getActivity())
			.setTitle(title)
			.setMessage(message)
			.setPositiveButton(R.string.button_ok,
				new DialogInterface.OnClickListener() {
					
					public void onClick(DialogInterface dialog, int whichButton) {
						((ConfirmationDialogCallbacks) getTargetFragment()).onPositive(object);
					}
					
				}
			)
			.setNegativeButton(R.string.button_cancel,
				new DialogInterface.OnClickListener() {
					
					public void onClick(DialogInterface dialog, int whichButton) {
						((ConfirmationDialogCallbacks) getTargetFragment()).onNegative(object);
					}
					
				}
			)
			.create();
		
	}
	
}
