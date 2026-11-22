package com.example.communityhealthworkerassistantance;

import android.content.Context;
import android.os.AsyncTask;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncManager {

    public interface SyncCallback {
        void onSyncComplete(int syncedCount);
    }

    private interface Step {
        void done(int count);
    }

    private static volatile boolean running = false;

    public static void syncPendingPatients(Context context, SyncCallback callback) {
        final Context app = context.getApplicationContext();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (running || user == null || !NetworkUtils.isOnline(app)) {
            if (callback != null) callback.onSyncComplete(0);
            return;
        }

        running = true;
        final String uid = user.getUid();

        uploadPending(app, uid, uploaded ->
                downloadFromCloud(app, uid, downloaded -> {
                    running = false;
                    if (callback != null) callback.onSyncComplete(uploaded + downloaded);
                }));
    }

    private static void uploadPending(Context app, String uid, Step next) {
        final AppDatabase db = AppDatabase.getInstance(app);
        final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        new AsyncTask<Void, Void, List<PatientEntity>>() {
            @Override
            protected List<PatientEntity> doInBackground(Void... voids) {
                return db.patientDao().getUnsyncedPatients();
            }

            @Override
            protected void onPostExecute(List<PatientEntity> pending) {

                if (pending.isEmpty()) {
                    next.done(0);
                    return;
                }

                final int[] remaining = {pending.size()};
                final int[] uploaded = {0};

                final Runnable oneFinished = () -> {
                    remaining[0]--;
                    if (remaining[0] == 0) {
                        next.done(uploaded[0]);
                    }
                };

                for (int i = 0; i < pending.size(); i++) {
                    final PatientEntity p = pending.get(i);

                    Map<String, Object> data = new HashMap<>();
                    data.put("name", p.name);
                    data.put("age", p.age);
                    data.put("village", p.village);
                    data.put("followUpDate", p.followUpDate);
                    data.put("latitude", p.latitude);
                    data.put("longitude", p.longitude);
                    data.put("ownerUid", uid);

                    firestore.collection("patients")
                            .add(data)
                            .addOnSuccessListener(docRef -> {
                                p.firestoreId = docRef.getId();
                                p.synced = true;

                                new AsyncTask<Void, Void, Void>() {
                                    @Override
                                    protected Void doInBackground(Void... voids) {
                                        db.patientDao().update(p);
                                        return null;
                                    }

                                    @Override
                                    protected void onPostExecute(Void result) {
                                        uploaded[0]++;
                                        oneFinished.run();
                                    }
                                }.execute();
                            })
                            .addOnFailureListener(e -> oneFinished.run());
                }
            }
        }.execute();
    }

    private static void downloadFromCloud(Context app, String uid, Step next) {
        final AppDatabase db = AppDatabase.getInstance(app);
        final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        firestore.collection("patients")
                .whereEqualTo("ownerUid", uid)
                .get()
                .addOnSuccessListener((QuerySnapshot snapshot) -> {

                    new AsyncTask<Void, Void, Integer>() {
                        @Override
                        protected Integer doInBackground(Void... voids) {
                            int added = 0;

                            for (QueryDocumentSnapshot doc : snapshot) {
                                if (db.patientDao().getByFirestoreId(doc.getId()) != null) {
                                    continue;
                                }

                                String name = doc.getString("name");
                                if (name == null) continue;

                                Long age = doc.getLong("age");
                                Double lat = doc.getDouble("latitude");
                                Double lng = doc.getDouble("longitude");

                                PatientEntity entity = new PatientEntity(
                                        name,
                                        age != null ? age.intValue() : 0,
                                        doc.getString("village"),
                                        doc.getString("followUpDate"),
                                        true
                                );
                                entity.latitude = lat != null ? lat : 0.0;
                                entity.longitude = lng != null ? lng : 0.0;
                                entity.firestoreId = doc.getId();

                                db.patientDao().insert(entity);
                                added++;
                            }

                            return added;
                        }

                        @Override
                        protected void onPostExecute(Integer added) {
                            next.done(added);
                        }
                    }.execute();
                })
                .addOnFailureListener(e -> next.done(0));
    }
}