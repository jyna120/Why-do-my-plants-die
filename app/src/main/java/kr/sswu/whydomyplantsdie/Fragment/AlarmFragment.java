package kr.sswu.whydomyplantsdie.Fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.storage.FirebaseStorage;

import java.util.ArrayList;

import kr.sswu.whydomyplantsdie.AlarmUploadActivity;
import kr.sswu.whydomyplantsdie.Model.AlarmModel;
import kr.sswu.whydomyplantsdie.R;
import kr.sswu.whydomyplantsdie.databinding.ItemAlarmBinding;

import static android.widget.Toast.LENGTH_SHORT;

public class AlarmFragment extends Fragment {

    private String uid;
    private RecyclerView recyclerView;
    private RecyclerView.Adapter adapter;
    private RecyclerView.LayoutManager layoutManager;
    private ArrayList<AlarmModel> alarmModels;
    private FirebaseDatabase database;
    private FirebaseStorage firebaseStorage;
    private FirebaseUser user;
    private FloatingActionButton btnAddAlarm;
    private View view;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_alarm, container, false);

        user = FirebaseAuth.getInstance().getCurrentUser();
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        btnAddAlarm = view.findViewById(R.id.btn_addAlarm);
        recyclerView = view.findViewById(R.id.alarm_recyclerview);
        recyclerView.setHasFixedSize(true); // 리사이클러뷰 기존성능 강화
        layoutManager = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(new AlarmAdapter(getActivity(), alarmModels)); // 리사이클러뷰에 어댑터 연결

        alarmModels = new ArrayList<>(); // User 객체를 담을 어레이 리스트 (어댑터쪽으로)
        database = FirebaseDatabase.getInstance(); // 파이어베이스 데이터베이스 연동
        firebaseStorage = FirebaseStorage.getInstance();

        //alarm upload activity로 이동
        btnAddAlarm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getContext(), AlarmUploadActivity.class);
                startActivity(intent);
            }
        });

        //fcm cloud messaging token
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.w("FCM Log", "getInstanceId failed", task.getException());
                            return;
                        }
                        String token = task.getResult();

                        Log.d("FCM Log", "FCM 토큰: " + token);
                    }
                });

        return view;
    }

    public class AlarmAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final ArrayList<AlarmModel> alarmList;
        private final ArrayList<String> uidList;

        public AlarmAdapter(FragmentActivity activity, ArrayList<AlarmModel> alarmModels) {
            alarmList = new ArrayList<>();
            uidList = new ArrayList<>();

            FirebaseDatabase.getInstance().getReference().child("alarm").orderByChild("uid").equalTo(user.getUid()).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    alarmList.clear();
                    uidList.clear();

                    for (DataSnapshot snapshot1 : snapshot.getChildren()) {
                        alarmList.add(snapshot1.getValue(AlarmModel.class));
                        uidList.add(snapshot1.getKey());
                    }
                    notifyDataSetChanged();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alarm, parent, false);
            return new CustomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final int finalPosition = position;
            final ItemAlarmBinding binding = ((CustomViewHolder) holder).getBinding();

            RequestOptions requestOptions = new RequestOptions();
            requestOptions = requestOptions.transforms(new CenterCrop(), new RoundedCorners(16));

            Glide.with(holder.itemView.getContext())
                    .load(alarmList.get(position).getImageUrl())
                    .centerCrop()
                    .apply(requestOptions).into((binding.itemAlarmImage));
            binding.itemAlarmPlantName.setText(alarmList.get(position).getPlantName());
            binding.itemAlarmHeart.setText("입양  " + alarmList.get(position).getHeart());
            binding.itemAlarmCycle.setText(alarmList.get(position).getCycle() + " 주기");


            //fcm 토픽 제어
            //FirebaseMessaging.getInstance().subscribeToTopic("90");
            binding.itemAlarmBtnOnoff.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pushOnOff(finalPosition);
                }
            });
            if (alarmList.get(position).pushOnOff == 1) {
                binding.itemAlarmBtnOnoff.setImageResource(R.drawable.icon_switch_on);

                switch (alarmList.get(position).getCycle()) {
                    case "물주기":
                        break;
                    case "1일":
                        FirebaseMessaging.getInstance().subscribeToTopic("day1");
                        break;
                    case "2일":
                        FirebaseMessaging.getInstance().subscribeToTopic("day2");
                        break;
                    case "3일":
                        FirebaseMessaging.getInstance().subscribeToTopic("day3");
                        break;
                    case "5일":
                        FirebaseMessaging.getInstance().subscribeToTopic("day5");
                        break;
                    case "10일":
                        FirebaseMessaging.getInstance().subscribeToTopic("day10");
                        break;
                    case "15일":
                        FirebaseMessaging.getInstance().subscribeToTopic("day15");
                        break;
                    case "20일":
                        FirebaseMessaging.getInstance().subscribeToTopic("day20");
                        break;
                    case "한 달":
                        FirebaseMessaging.getInstance().subscribeToTopic("day30");
                        break;
                    case "두 달":
                        FirebaseMessaging.getInstance().subscribeToTopic("day60");
                        break;
                    case "세 달":
                        FirebaseMessaging.getInstance().subscribeToTopic("day90");
                        break;
                }
            } else if (alarmList.get(position).pushOnOff == 0) {
                binding.itemAlarmBtnOnoff.setImageResource(R.drawable.icon_switch_off);

                switch (alarmList.get(position).getCycle()) {
                    case "물주기":
                        break;
                    case "1일":
                        FirebaseMessaging.getInstance().unsubscribeFromTopic("day1");
                        break;
                    case "2일":
                        FirebaseMessaging.getInstance().unsubscribeFromTopic("day2");
                        break;
                    case "3일":
                        FirebaseMessaging.getInstance().unsubscribeFromTopic("day3");
                        break;
                    case "5일":
                        FirebaseMessaging.getInstance().unsubscribeFromTopic("day5");
                        break;
                    case "10일":
                        FirebaseMessaging.getInstance().unsubscribeFromTopic("day10");
                        break;
                    case "15일":
                        FirebaseMessaging.getInstance().unsubscribeFromTopic("day15");
                        break;
                    case "20일":
                        FirebaseMessaging.getInstance().unsubscribeFromTopic("day20");
                        break;
                    case "한 달":
                        FirebaseMessaging.getInstance().unsubscribeFromTopic("day30");
                        break;
                    case "두 달":
                        FirebaseMessaging.getInstance().unsubscribeFromTopic("day60");
                        break;
                    case "세 달":
                        FirebaseMessaging.getInstance().unsubscribeFromTopic("day90");
                        break;
                }
            }

            //fcm cloud messaging token
            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(new OnCompleteListener<String>() {
                        @Override
                        public void onComplete(@NonNull Task<String> task) {
                            if (!task.isSuccessful()) {
                                Log.w("FCM Log", "getInstanceId failed", task.getException());
                                return;
                            }
                            String token = task.getResult();

                            Log.d("FCM Log", "FCM 토큰: " + token);
                        }
                    });


            // 삭제 bottomsheet
            LayoutInflater inflater = (LayoutInflater) requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.bottomsheet_delete_post, null, false);
            final BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(getContext());
            bottomSheetDialog.setContentView(view);

            // 알람 아이템 삭제
            binding.itemAlarmLayout.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                        bottomSheetDialog.show();

                        view.findViewById(R.id.txt_delete).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                deleteAlarm(position);
                                bottomSheetDialog.dismiss();

                            }
                        });
                    return true;
                }
            });
        }

        private void deleteAlarm(int position) {
            firebaseStorage.getReference().child("alarm").child(alarmList.get(position).image).delete().addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {

                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {

                }
            });

            database.getReference().child("alarm").child(uidList.get(position)).removeValue().addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Toast.makeText(getContext(), "삭제 완료", LENGTH_SHORT).show();
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(getContext(), "삭제 실패", LENGTH_SHORT).show();
                }
            });
        }

        private void pushOnOff (int position) {
            final int finalPosition = position;
            FirebaseDatabase.getInstance().getReference("alarm").child(uidList.get(position))
                    .runTransaction(new Transaction.Handler() {
                        @NonNull
                        @Override
                        public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                            AlarmModel alarmModel = currentData.getValue(AlarmModel.class);
                            if (alarmModel == null) {
                                return Transaction.success(currentData);
                            }
                            if (alarmModel.pushOnOff == 0) {
                                alarmModel.pushOnOff = alarmModel.pushOnOff + 1;
                            } else if (alarmModel.pushOnOff == 1) {
                                alarmModel.pushOnOff = alarmModel.pushOnOff - 1;
                            }
                            currentData.setValue(alarmModel);
                            return Transaction.success(currentData);
                        }

                        @Override
                        public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {

                        }
                    });
        }

        @Override
        public int getItemCount() {
            return (alarmList != null ? alarmList.size() : 0);
        }

        public class CustomViewHolder extends RecyclerView.ViewHolder {

            private ItemAlarmBinding binding;

            public CustomViewHolder(@NonNull View itemView) {
                super(itemView);
                binding = DataBindingUtil.bind(itemView);
            }

            ItemAlarmBinding getBinding() {
                return binding;
            }
        }
    }
}