package ca.macewan.capstone;

import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.List;
import java.util.Map;

@IgnoreExtraProperties
public class User {
    public String email;
    public String name;
    public String role;
    public String phone;
    public List<String> projects;
    public String picture;
    public Map<String, Boolean> availability;

    public User() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    public User(String email, String name, String role) {
        this.email = email;
        this.name = name;
        this.role = role;
    }

    public User(String email, String name, String role, String phone) {
        this.email = email;
        this.name = name;
        this.role = role;
        this.phone = phone;
    }
}
