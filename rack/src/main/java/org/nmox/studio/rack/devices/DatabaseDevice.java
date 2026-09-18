package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * NEPTUNE Database Console: Ping SQL databases and execute migrations.
 * Connects to PostgreSQL, MySQL, SQLite, or triggers framework migrations (Prisma, Ecto, Django).
 */
public class DatabaseDevice extends CommandDevice {

    private static final String[] DB_TYPES = {"Postgres", "MySQL", "SQLite", "Prisma", "Ecto", "Django"};
    
    private final Knob dbTypeKnob;
    private final LcdDisplay connLcd;
    private final Led connectedLed;
    private final Led errorLed;
    
    /**
     * The verb that actually launched, captured at launch and consulted by
     * onFinished (the STELLAR v1.135.0 exit-attribution law): the CONNECTED
     * gate is a ping's verdict, never a migration's. Also what
     * {@link #buildCommand()} builds for the button tooltips and CI export.
     */
    private volatile String lastAction = "ping";

    public DatabaseDevice() {
        super("database", "NEPTUNE", "DATABASE CONSOLE", new Color(51, 153, 255), 3);

        dbTypeKnob = place(new Knob("DB TYPE", DB_TYPES, 0), 44, 40);
        
        connLcd = place(new LcdDisplay(220, 1), 120, 52);
        connLcd.setText("localhost/db_name");
        connLcd.setEditable("Connection URL or database name");

        RackButton ping = place(new RackButton("PING", RackStyle.GO), 350, 52);
        RackButton migrate = place(new RackButton("MIGRATE", new Color(99, 197, 70)), 414, 52);
        
        connectedLed = place(new Led("CONNECTED", RackStyle.GO), 350, 100);
        errorLed = place(new Led("ERROR", RackStyle.STOP), 424, 100);

        ping.addActionListener(e -> ping());
        migrate.addActionListener(e -> migrate());

        addInPort("ping", "PING", SignalType.TRIGGER);
        addInPort("migrate", "MIGRATE", SignalType.TRIGGER);
        addOutPort("connected", "RUNNING", SignalType.GATE);

        param("dbType", dbTypeKnob);
        param("conn", connLcd);
    }

    private void ping() {
        lastAction = "ping";
        onEdt(() -> {
            connectedLed.setOn(false);
            errorLed.setOn(false);
        });
        launch(buildCommand());
    }

    private void migrate() {
        lastAction = "migrate";
        onEdt(() -> {
            connectedLed.setOn(false);
            errorLed.setOn(false);
        });
        launch(buildCommand());
    }

    @Override
    protected List<String> buildCommand() {
        String type = DB_TYPES[dbTypeKnob.getSelectedIndex()];
        String target = connLcd.getText().trim();
        
        List<String> cmd = new ArrayList<>();
        
        if ("ping".equals(lastAction)) {
            switch (type) {
                case "Postgres":
                    cmd.add("psql");
                    if (!target.isEmpty()) {
                        cmd.addAll(List.of("-d", target));
                    }
                    cmd.addAll(List.of("-c", "SELECT 1;"));
                    break;
                case "MySQL":
                    cmd.add("mysql");
                    if (!target.isEmpty()) {
                        cmd.addAll(List.of("-D", target));
                    }
                    cmd.addAll(List.of("-e", "SELECT 1;"));
                    break;
                case "SQLite":
                    cmd.add("sqlite3");
                    cmd.add(target.isEmpty() ? ":memory:" : target);
                    cmd.add("SELECT 1;");
                    break;
                case "Prisma":
                    cmd.addAll(List.of("npx", "prisma", "validate"));
                    break;
                case "Ecto":
                    cmd.addAll(List.of("mix", "ecto.status"));
                    break;
                case "Django":
                    cmd.addAll(List.of("python", "manage.py", "check"));
                    break;
                default:
                    break;
            }
        } else { // migrate
            switch (type) {
                case "Postgres":
                    // run migrate.sql against the connection, not sqlite3
                    cmd.add("psql");
                    if (!target.isEmpty()) {
                        cmd.addAll(List.of("-d", target));
                    }
                    cmd.addAll(List.of("-f", "migrate.sql"));
                    break;
                case "MySQL":
                    cmd.add("mysql");
                    if (!target.isEmpty()) {
                        cmd.addAll(List.of("-D", target));
                    }
                    cmd.addAll(List.of("-e", "source migrate.sql"));
                    break;
                case "SQLite":
                    cmd.addAll(List.of("sqlite3", target.isEmpty() ? "dev.db" : target, ".read migrate.sql"));
                    break;
                case "Prisma":
                    cmd.addAll(List.of("npx", "prisma", "db", "push"));
                    break;
                case "Ecto":
                    cmd.addAll(List.of("mix", "ecto.migrate"));
                    break;
                case "Django":
                    cmd.addAll(List.of("python", "manage.py", "migrate"));
                    break;
                default:
                    break;
            }
        }
        return cmd;
    }

    @Override
    protected void onFinished(int exitCode) {
        boolean success = (exitCode == 0);
        onEdt(() -> {
            connectedLed.setOn(success);
            errorLed.setOn(!success);
        });
        // CONNECTED answers "can I reach the database?" — a ping's verdict.
        // A migration's exit used to drive the same gate, so a failed
        // migrate.sql read as "not connected" and a green one as "connected"
        // without any connection having been tested (the 2026-09-17 audit)
        if ("ping".equals(lastAction)) {
            emit("connected", Signal.gate(success));
        }
    }

    @Override
    public void receive(Port in, Signal signal) {
        if (signal.type() == SignalType.TRIGGER) {
            switch (in.getId()) {
                case "ping" -> ping();
                case "migrate" -> migrate();
                // RUN is always a ping. It used to fall to primaryAction →
                // buildCommand(), which switches on whichever BUTTON was
                // pressed last: a RUN cable pinged until someone pressed
                // MIGRATE on the faceplate, after which the same cable ran
                // migrations (the 2026-09-17 rack audit)
                case "run" -> ping();
                default -> super.receive(in, signal);
            }
        }
    }
}
