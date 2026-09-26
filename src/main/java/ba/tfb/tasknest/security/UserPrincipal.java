package ba.tfb.tasknest.security;

import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;

    @Getter(AccessLevel.NONE)
    private final String passwordHash;

    private final AccountStatus accountStatus;
    private final List<GrantedAuthority> authorities;

    public static UserPrincipal withCredentials(User user) {
        return new UserPrincipal(user, user.getPasswordHash());
    }

    public static UserPrincipal withoutCredentials(User user) {
        return new UserPrincipal(user, null);
    }

    private UserPrincipal(User user, String passwordHash) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = passwordHash;
        this.accountStatus = user.getAccountStatus();
        this.authorities = user.getRoles().stream()
                .map(role -> (GrantedAuthority)
                        new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountStatus != AccountStatus.SUSPENDED;
    }
}
