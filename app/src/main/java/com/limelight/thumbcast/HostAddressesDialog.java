package com.limelight.thumbcast;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
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
 * Dialog „Adresy połączenia” z menu PC (przytrzymanie albo ⋮ w PcView): adresy w grupach „W domu (LAN)” i
 * „Poza domem” w kolejności łączenia, aktywny z plakietką „Używany”; kosz przy wierszu = usuń (z potwierdzeniem),
 * „Dodaj adres” = nowy adres alternatywny (od 12.11.2026, U16: wcześniej tap w wiersz od razu pytał o usunięcie).
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

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.thumbcast_launcher_addresses_title, computer.name))
                .setNeutralButton(R.string.thumbcast_addresses_add, (d, w) -> showAdd(activity, binder, computer))
                .setPositiveButton(R.string.thumbcast_addresses_close, null);
        if (entries.isEmpty()) {
            builder.setMessage(R.string.thumbcast_addresses_empty);
            builder.show();
            return;
        }
        final AlertDialog[] dialog = new AlertDialog[1];
        builder.setView(buildList(activity, entries, entry -> {
            if (dialog[0] != null) {
                dialog[0].dismiss();
            }
            confirmRemove(activity, binder, computer, entry);
        }));
        dialog[0] = builder.show();
    }

    private interface RemoveAction {
        void remove(AddressEntry entry);
    }

    /** Lista z nagłówkami grup i koszem przy każdym adresie (thumbcast_pc_address_header / _row). */
    private static View buildList(Activity activity, List<AddressEntry> entries, RemoveAction onRemove) {
        LayoutInflater inflater = LayoutInflater.from(activity);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        AddressGroup[] groups = {AddressGroup.LAN, AddressGroup.ALTERNATIVE};
        for (AddressGroup group : groups) {
            boolean header = false;
            for (final AddressEntry entry : entries) {
                if (entry.getGroup() != group) {
                    continue;
                }
                if (!header) {
                    TextView title = (TextView) inflater.inflate(R.layout.thumbcast_pc_address_header, column, false);
                    title.setText(group == AddressGroup.LAN ? R.string.thumbcast_launcher_addresses_group_home
                            : R.string.thumbcast_launcher_addresses_group_away);
                    column.addView(title);
                    header = true;
                }
                View row = inflater.inflate(R.layout.thumbcast_pc_address_row, column, false);
                String host = display(entry.getEndpoint());
                ((TextView) row.findViewById(R.id.thumbcast_address_host)).setText(host);
                ((TextView) row.findViewById(R.id.thumbcast_address_source)).setText(sourceLabel(entry));
                row.findViewById(R.id.thumbcast_address_active).setVisibility(entry.getActive() ? View.VISIBLE : View.GONE);
                View remove = row.findViewById(R.id.thumbcast_address_remove);
                String removeLabel = activity.getString(R.string.thumbcast_launcher_addresses_remove_for, host);
                remove.setContentDescription(removeLabel);
                remove.setTooltipText(removeLabel);
                remove.setOnClickListener(v -> onRemove.remove(entry));
                column.addView(row);
            }
        }
        TextView note = new TextView(activity);
        note.setTextAppearance(R.style.ThumbcastText_BodySecondary);
        note.setText(R.string.thumbcast_launcher_addresses_note);
        int pad = activity.getResources().getDimensionPixelSize(R.dimen.thumbcast_space_large);
        int small = activity.getResources().getDimensionPixelSize(R.dimen.thumbcast_space_small);
        note.setPadding(pad, small, pad, small);
        column.addView(note);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(column);
        return scroll;
    }

    private static int sourceLabel(AddressEntry entry) {
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
        return sourceRes;
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
