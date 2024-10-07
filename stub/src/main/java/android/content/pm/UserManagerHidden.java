package android.content.pm;

import android.os.UserManager;

import java.util.List;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(UserManager.class)
public class UserManagerHidden {
    public native List<UserInfo> getUsers();
}
