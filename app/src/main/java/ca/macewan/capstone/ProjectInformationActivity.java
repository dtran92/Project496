package ca.macewan.capstone;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;
import com.squareup.picasso.Picasso;

import java.util.List;
import java.util.Objects;

import ca.macewan.capstone.adapter.SharedMethods;
import uk.co.onemandan.materialtextview.MaterialTextView;

public class ProjectInformationActivity extends AppCompatActivity {
    FirebaseFirestore db;
    String projectID;
    DocumentReference projectRef;
    String email;
    private Menu menu;
    private DocumentReference userRef;
    private Project project;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View projectView;
    private View profButtonsLayout;
    private boolean isSupervisor;
    private View markAsCompleteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get action bar and show back button
        assert getSupportActionBar() != null;
        getSupportActionBar().setTitle("Project Information");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.activity_project_information);
        db = FirebaseFirestore.getInstance();
        email = FirebaseAuth.getInstance().getCurrentUser().getEmail();

        setUp();
    }

    private void setUp() {
        EventCompleteListener projectNotNull = new EventCompleteListener() {
            @Override
            public void onComplete() {
                updateView(project);

                swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
                swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        updateProject();
                        swipeRefreshLayout.setRefreshing(false);
                    }
                });
            }
        };

        projectID = getIntent().getExtras().getString("projectID");
        isSupervisor = getIntent().getExtras().getBoolean("isSupervisor");
        projectRef = db.collection("Projects").document(projectID);
        userRef = db.collection("Users").document(email);
        projectView = findViewById(R.id.projectLayout);
        profButtonsLayout = findViewById(R.id.profButtonsLayout);
        markAsCompleteButton = projectView.findViewById(R.id.markAsCompleteButton);
        markAsCompleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MaterialAlertDialogBuilder(ProjectInformationActivity.this)
                    .setMessage("Mark project as complete?")
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Move project id to from projects to completed
                            userRef.update("completed", FieldValue.arrayUnion(projectRef.getId()));
                            userRef.update("projects", FieldValue.arrayRemove(projectRef.getId()));
                            // Add project to the "Complete" collection and remove it from Projects
                            projectRef.update("isComplete", true);
                            // Remove supervisor and members in project
                            db.collection("Users").whereArrayContains("projects", projectID)
                                    .get()
                                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                        @Override
                                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                            for (QueryDocumentSnapshot supervisor : task.getResult()) {
                                                supervisor.getReference().update("projects", FieldValue.arrayRemove(projectRef.getId()));
                                                supervisor.getReference().update("completed", FieldValue.arrayUnion(projectRef.getId()));
                                            }
                                        }
                                    });
                            // Remove invites from professors
                            db.collection("Users").whereArrayContains("invited", projectID)
                                    .get()
                                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                        @Override
                                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                            for (QueryDocumentSnapshot supervisor : task.getResult()) {
                                                supervisor.getReference().update("invited", FieldValue.arrayRemove(projectRef.getId()));
                                            }
                                        }
                                    });
                            finish();
                        }
                    })
                    .show();
            }
        });

        project = getIntent().getExtras().getParcelable("project");
        if (project != null && !isSupervisor) {
            projectNotNull.onComplete();
        } else {
            projectRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    project = task.getResult().toObject(Project.class);
                    assert project != null;
                    project.setProjectRef(task.getResult().getReference());
                    project.setAllStrings(new EventCompleteListener() {
                        @Override
                        public void onComplete() {
                            projectNotNull.onComplete();
                        }
                    });
                    if (isSupervisor) {
                        profButtonsSetUp();
                    }
                }
            });
        }
    }

    private void profButtonsSetUp() {
        View button_Accept = profButtonsLayout.findViewById(R.id.button_Accept);
        View button_Decline = profButtonsLayout.findViewById(R.id.button_Decline);

        button_Decline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MaterialAlertDialogBuilder(ProjectInformationActivity.this)
                        .setMessage("Decline the request?")
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // remove the invitation from array
                                userRef.update("invited", FieldValue.arrayRemove(projectRef.getId()));
                                projectRef.update("supervisorsPending", userRef);
                                profButtonsLayout.setVisibility(View.GONE);
                                updateUser();
                            }
                        })
                        .show();
            }
        });

        button_Accept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MaterialAlertDialogBuilder(ProjectInformationActivity.this)
                        .setMessage("Agree to supervise?")
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // remove supervisor from pending supervisors list
                                projectRef.update("supervisorsPending", FieldValue.arrayRemove(userRef));
                                // add prof to supervisor list
                                projectRef.update("supervisors", FieldValue.arrayUnion(userRef));
                                // add request to accepted array
                                userRef.update("projects", FieldValue.arrayUnion(projectRef.getId()));
                                // remove request from invited array
                                userRef.update("invited", FieldValue.arrayRemove(projectRef.getId()));
                                profButtonsLayout.setVisibility(View.GONE);
                                NotifClient notifier = new NotifClient();
                                userRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                    @Override
                                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                        if (task.isSuccessful()) {
                                            notifier.payloadThread(notifier.payloadConstructor(
                                                    String.format("%s has a new supervisor!", project.getName()),
                                                    String.format("%s is now supervising the project.", task.getResult().get("name")),
                                                    "supervisorJoin",
                                                    projectID));
                                        }
                                    }
                                });
                                updateProject();
                                updateUser();
                            }
                        })
                        .show();
            }
        });

        // disable the button for prof that was not invited to supervise
        userRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    List<String> invitedProjList = (List<String>) task.getResult().get("invited");
                    if (!invitedProjList.contains(projectRef.getId())) {
                        profButtonsLayout.setVisibility(View.GONE);
                    } else {
                        profButtonsLayout.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
    }

    private void updateProject() {
        projectRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                Project project = task.getResult().toObject(Project.class);
                assert project != null;
                project.setProjectRef(task.getResult().getReference());
                project.setAllStrings(new EventCompleteListener() {
                    @Override
                    public void onComplete() {
                        updateView(project);
                    }
                });
            }
        });
    }

    private void updateView(Project project) {
        MaterialTextView textViewTitle = (MaterialTextView) projectView.findViewById(R.id.textViewTitle);
        MaterialTextView textViewCreator = (MaterialTextView) projectView.findViewById(R.id.textViewCreator);
        MaterialTextView textViewSemesterAndYear = (MaterialTextView) projectView.findViewById(R.id.textViewSemesterAndYear);
        MaterialTextView textViewSupervisors = (MaterialTextView) projectView.findViewById(R.id.textViewSupervisors);
        MaterialTextView textViewMembers = (MaterialTextView) projectView.findViewById(R.id.textViewMembers);
        MaterialTextView textViewDescription = (MaterialTextView) projectView.findViewById(R.id.textViewDescription);
        MaterialTextView textViewTags = (MaterialTextView) projectView.findViewById(R.id.textViewTags);
        View textViewImages = projectView.findViewById(R.id.textViewImages);
        LinearLayout linearLayoutImages = (LinearLayout) projectView.findViewById(R.id.linearLayoutImages);

        textViewTitle.setContentText(project.getName(), null);
        textViewCreator.setContentText(project.getCreatorString(), null);
        textViewSemesterAndYear.setContentText(project.getSemester() + " " + project.getYear(), null);
        setSupervisorsTextView(project, textViewSupervisors);
        setMembersTextView(project, textViewMembers);
        textViewDescription.setContentText(project.getDescription(), null);
        setTagsTextView(project, textViewTags);
        setImageViews(project, textViewImages, linearLayoutImages);
    }

    private void setImageViews(Project project, View textViewImages, LinearLayout linearLayoutImages) {
        List<String> imagePaths = project.getImagePaths();
        if (SharedMethods.listIsEmpty(imagePaths)) {
            textViewImages.setVisibility(View.GONE);
            linearLayoutImages.setVisibility(View.GONE);
            return;
        }
        textViewImages.setVisibility(View.VISIBLE);
        linearLayoutImages.setVisibility(View.VISIBLE);
        linearLayoutImages.removeAllViews();
        for (String imagePath : imagePaths) {
            ImageView newImage = new ImageView(this);
            newImage.setAdjustViewBounds(true);
            linearLayoutImages.addView(newImage);
            Picasso.get().load(imagePath).into(newImage);
        }
    }

    private void setSupervisorsTextView(Project project, MaterialTextView textViewSupervisors) {
        List<String> supervisorsStringList = project.getSupervisorsStringList();
        List<String> supervisorsPendingStringList = project.getSupervisorsPendingStringList();

        if (SharedMethods.listIsEmpty(supervisorsStringList)) {
            // No supervisors, check if any are pending
            if (SharedMethods.listIsEmpty(supervisorsPendingStringList) || project.getIsComplete()) {
                textViewSupervisors.setContentText("None", null);
                return;
            }
            textViewSupervisors.setContentText("Pending", null);
            return;
        }
        textViewSupervisors.setContentText(String.join("\n", supervisorsStringList), null);
    }

    private void setMembersTextView(Project project, MaterialTextView textViewMembers) {
        List<String> membersStringList = project.getMembersStringList();
        if (SharedMethods.listIsEmpty(membersStringList)) {
            textViewMembers.setContentText("None", null);
            return;
        }
        textViewMembers.setContentText(String.join("\n", membersStringList), null);
    }

    private void setTagsTextView(Project project, MaterialTextView textViewTags) {
        List<String> tags = project.getTags();
        if (SharedMethods.listIsEmpty(tags)) {
            textViewTags.setVisibility(View.GONE);
            return;
        }
        textViewTags.setContentText(String.join("\n", tags), null);
    }

    private String getSupervisorsString(String supervisorsString) {
        if (Objects.equals(supervisorsString, "")) {
            return "Pending";
        }
        return supervisorsString;
    }

    private String getMembersString(String membersString) {
        if (Objects.equals(membersString, "")) {
            return "None";
        }
        return membersString;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_information_proposal, menu);
        this.menu = menu;
        updateUser();
        return super.onCreateOptionsMenu(menu);
    }

    private void updateUser() {
        db.collection("Users")
                .document(FirebaseAuth.getInstance().getCurrentUser().getEmail())
                .get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            User user = task.getResult().toObject(User.class);
                            refreshButtons(user);
                        }
                    }
                });
    }

    private void refreshButtons(User user) {
        menu.findItem(R.id.action_edit).setVisible(false);
        menu.findItem(R.id.action_delete).setVisible(false);
        menu.findItem(R.id.action_quit).setVisible(false);
        menu.findItem(R.id.action_join).setVisible(false);

        if (!project.getIsComplete()) {
            boolean userJoined = false;
            if (user.projects == null) {
                menu.findItem(R.id.action_join).setVisible(true);
                return;
            }

            for (String projectId : user.projects) {
                if (projectId.equals(projectRef.getId())) {
                    userJoined = true;
                    break;
                }
            }
            if (!userJoined) {
                if (!isSupervisor)
                    menu.findItem(R.id.action_join).setVisible(true);
                return;
            }

            projectRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    if (task.isSuccessful()) {
                        task.getResult()
                                .getDocumentReference("creator")
                                .get()
                                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                    @Override
                                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                        if (task.isSuccessful()) {
                                            String creatorEmail = task.getResult().getString("email");
                                            if (Objects.equals(creatorEmail, email)) {
                                                menu.findItem(R.id.action_delete).setVisible(true);
                                                markAsCompleteButton.setVisibility(View.VISIBLE);
                                            } else {
                                                menu.findItem(R.id.action_quit).setVisible(true);
                                            }
                                            menu.findItem(R.id.action_edit).setVisible(true);
                                        }
                                    }
                                });
                    }
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit) {
            Intent intent = new Intent(getApplicationContext(), ProposalEditActivity.class);
            intent.putExtra("projectID", projectID);
            intent.putExtra("email", email);
            startActivity(intent);
        } else if (id == R.id.action_quit) {
            SharedMethods.quitProject(userRef, projectRef, ProjectInformationActivity.this, new EventCompleteListener() {
                @Override
                public void onComplete() {
                    // Update user to change options menu buttons if needed
                    updateUser();
                    // Update project to show any changes made
                    updateProject();
                }
            });
        } else if (id == R.id.action_delete) {
            SharedMethods.deleteProject(userRef, projectRef, ProjectInformationActivity.this, new EventCompleteListener() {
                @Override
                public void onComplete() {
                    finish();
                }
            });
        } else if (id == R.id.action_join) {
            SharedMethods.joinProject(userRef, projectRef, ProjectInformationActivity.this, new EventCompleteListener() {
                @Override
                public void onComplete() {
                    // Update user to change options menu buttons if needed
                    updateUser();
                    // Update project to show any changes made
                    updateProject();
                    // Subscribe to notifications for this project
                    FirebaseMessaging.getInstance().subscribeToTopic(projectRef.getId());
                }
            });
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

}