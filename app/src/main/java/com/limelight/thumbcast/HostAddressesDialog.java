package com.limelight.thumbcast;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;

import java.util.List;

import app.thumbcast.android.net.AddressEntry;
import app.thumbcast.android.net.AddressGroup;
import app.thumbcast.android.net.Endpoint;
import app.thumbcast.android.net.HostAddresses;

/**
 * Dialog „Adresy połączenia” z menu PC (długie przytrzymanie w PcView): adresy LAN i alternatywne
 * w kolejności łączenia, aktywny oznaczony; tap = usuń, „Dodaj adres” = nowy adres alternatywny.
 * Zmiany idą przez {@code ComputerManagerBinder.addAddress/removeAddress} (zapis do bazy, bez parowania).
 */
public final class HostAddressesDialog {
    private HostAddressesDialog() {}

    public static void show(final Activity activity, final ComputerManagerService.ComputerManagerBinder binder,
                            final ComputerDetails computer) {
        if (activity.isFinishing() || binder == null || computer == null || computer.uuid == null) {
            return;
        }

        final List<AddressEntry> entries = ThumbcastAddressBook.entries(computer);
        String[] items = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            items[i] = describe(activity, entries.get(i));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.thumbcast_addresses_title, computer.name))
                .setNeutralButton(R.string.thumbcast_addresses_add, (d, w) -> showAdd(activity, binder, computer))
                .setPositiveButton(R.string.thumbcast_addresses_close, null);
        if (entries.isEmpty()) {
            builder.setMessage(R.string.thumbcast_addresses_empty);
        } else {
            builder.setItems(items, (d, which) -> confirmRemove(activity, binder, computer, entries.get(which)));
        }
        builder.show();
    }

    private static String describe(Activity activity, AddressEntry entry) {
        String group = activity.getString(entry.getGroup() == AddressGroup.LAN
                ? R.string.thumbcast_addresses_group_lan : R.string.thumbcast_addresses_group_alt);
        int sourceRes;
        switch (entry.getSource()) {
            case LOCAL:
                sourceRes = R.string.thumbcast_addresses_source_local;
                break;
            case REMOTE:
                sourceRes = R.string.thumbcast_addresses_source_remote;
                break;
            case IPV6:
                sourceRes = R.string.thumbcast_addresses_source_ipv6;
                break;
            case MANUAL:
            default:
                sourceRes = R.string.thumbcast_addresses_source_manual;
                break;
        }
        String text = activity.getString(R.string.thumbcast_addresses_item, group,
                display(entry.getEndpoint()), activity.getString(sourceRes));
        if (entry.getActive()) {
            text = activity.getString(R.string.thumbcast_addresses_item_active, text);
        }
        return text;
    }

    private static String display(Endpoint e) {
        if (e.getPort() == HostAddresses.DEFAULT_HTTP_PORT) {
            return e.getHost();
        }
        return e.toString();
    }

    private static void confirmRemove(final Activity activity, final ComputerManagerService.ComputerManagerBinder binder,
                                      final ComputerDetails computer, final AddressEntry entry) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.thumbcast_addresses_remove_title)
                .setMessage(activity.getString(R.string.thumbcast_addresses_remove_message, display(entry.getEndpoint())))
                .setPositiveButton(R.string.thumbcast_addresses_remove, (d, w) -> edit(activity, binder, computer,
                        new ComputerDetails.AddressTuple(entry.getEndpoint().getHost(), entry.getEndpoint().getPort()), false))
                .setNegativeButton(android.R.string.cancel, (d, w) -> show(activity, binder, computer))
                .show();
    }

    private static void showAdd(final Activity activity, final ComputerManagerService.ComputerManagerBinder binder,
                                final ComputerDetails computer) {
        final EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint(R.string.thumbcast_addresses_add_hint);
        FrameLayout frame = new FrameLayout(activity);
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        frame.setPadding(pad, pad / 2, pad, 0);
        frame.addView(input);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.thumbcast_addresses_add)
                .setMessage(R.string.thumbcast_addresses_add_message)
                .setView(frame)
                .setPositiveButton(R.string.thumbcast_addresses_add_confirm, (d, w) -> {
                    Endpoint endpoint = HostAddresses.parseEndpoint(input.getText().toString());
                    if (endpoint == null) {
                        Toast.makeText(activity, R.string.thumbcast_addresses_invalid, Toast.LENGTH_SHORT).show();
                        showAdd(activity, binder, computer);
                        return;
                    }
                    for (AddressEntry e : ThumbcastAddressBook.entries(computer)) {
                        if (e.getEndpoint().equals(endpoint)) {
                            Toast.makeText(activity, R.string.thumbcast_addresses_exists, Toast.LENGTH_SHORT).show();
                            show(activity, binder, computer);
                            return;
                        }
                    }
                    edit(activity, binder, computer,
                            new ComputerDetails.AddressTuple(endpoint.getHost(), endpoint.getPort()), true);
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> show(activity, binder, computer))
                .show();
    }

    private static void edit(final Activity activity, final ComputerManagerService.ComputerManagerBinder binder,
                             final ComputerDetails computer, final ComputerDetails.AddressTuple address, final boolean add) {
        // Poza wątkiem UI: serwis czeka na networkLock trwającego odpytania PC.
        Thread t = new Thread(() -> {
            ComputerDetails updated = add ? binder.addAddress(computer.uuid, address)
                    : binder.removeAddress(computer.uuid, address);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) {
                    return;
                }
                Toast.makeText(activity, updated != null ? R.string.thumbcast_addresses_saved
                        : R.string.error_manager_not_running, Toast.LENGTH_SHORT).show();
                show(activity, binder, updated != null ? updated : computer);
            });
        });
        t.setName("Thumbcast address edit");
        t.start();
    }
}
