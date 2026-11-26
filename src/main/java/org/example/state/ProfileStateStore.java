package org.example.state;

import java.nio.file.Path;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProfileStateStore implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileStateStore.class);
    private final String profileId;
    private final UserStateRepository repository;
    private UserAppState state;

    private ProfileStateStore(String profileId, UserStateRepository repository, UserAppState state) {
        this.profileId = profileId;
        this.repository = repository;
        this.state = state;
    }

    public static ProfileStateStore open(String profileId, Path path) {
        if (profileId == null || profileId.isBlank()) {
            return new ProfileStateStore(profileId, null, UserAppState.defaults());
        }
        UserStateRepository repository = null;
        try {
            repository = UserStateRepository.open(path);
            return new ProfileStateStore(profileId, repository, repository.load(profileId));
        } catch (SQLException failure) {
            if (repository != null) {
                try {
                    repository.close();
                } catch (SQLException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            LOGGER.warn("User state unavailable; using session defaults", failure);
            return new ProfileStateStore(profileId, null, UserAppState.defaults());
        }
    }

    public UserAppState state() {
        return state;
    }

    public void save(UserAppState next) {
        if (repository == null || next.equals(state)) return;
        try {
            repository.save(profileId, next);
            state = next;
        } catch (SQLException failure) {
            LOGGER.warn("Could not save user state", failure);
        }
    }

    @Override
    public void close() {
        if (repository != null) {
            try {
                repository.close();
            } catch (SQLException failure) {
                LOGGER.warn("Could not close user state database", failure);
            }
        }
    }
}
