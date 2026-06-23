package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.DTO.IN.UserIn;
import com.kingdom.DTO.OUT.AdminStatsOut;
import com.kingdom.DTO.OUT.UserOut;
import com.kingdom.Enums.ProgressStatus;
import com.kingdom.Enums.SubscriptionStatus;
import com.kingdom.Enums.UserRole;
import com.kingdom.Model.*;
import com.kingdom.Repository.*;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final ModelMapper modelMapper;
    private final UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private final PlayerRepository playerRepository;
    private final KingdomRepository kingdomRepository;
    private final ChallengeRepository challengeRepository;
    private final LobbyRepository lobbyRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ChallengeProgressRepository challengeProgressRepository;

    public List<UserOut> getAllUsers() {
        List<UserOut> users = new ArrayList<>();
        for (User u : userRepository.findAll()) {
            users.add(modelMapper.map(u, UserOut.class));
        }
        return users;
    }

    public UserOut getUserById(Integer id) {
        User user = checkUser(id);
        return modelMapper.map(user, UserOut.class);
    }

    public void addUser(UserIn userIn) {
        User user = new User();
        user.setEmail(userIn.getEmail());
        user.setUsername(userIn.getUsername());
        user.setPhoneNumber(userIn.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(userIn.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        user.setRole(UserRole.USER);
        userRepository.save(user);
    }

    public void updateUser(Integer id, UserIn userIn) {
        User oldUser = checkUser(id);

        oldUser.setEmail(userIn.getEmail());
        oldUser.setUsername(userIn.getUsername());
        oldUser.setPhoneNumber(userIn.getPhoneNumber());
        oldUser.setPasswordHash(userIn.getPassword());

        userRepository.save(oldUser);
    }

    public void deleteUser(Integer id) {
        User user = checkUser(id);
        userRepository.delete(user);
    }

    public AdminStatsOut adminGetStatistics() {
        AdminStatsOut stats = new AdminStatsOut();
        stats.setTotalPlayers(playerRepository.count());
        stats.setTotalUsers(userRepository.count());
        stats.setTotalKingdoms(kingdomRepository.count());
        stats.setTotalChallenges(challengeRepository.count());
        stats.setTotalLobbies(lobbyRepository.count());
        stats.setActiveSubscriptions(subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE));
        stats.setPendingChallengeProgress(challengeProgressRepository.countByStatus(ProgressStatus.JOINED));
        return stats;
    }


    public List<Player> adminGetAllPlayers() {
        return playerRepository.findAll();
    }

    public List<Challenge> adminGetAllChallenges() {
        return challengeRepository.findAll();
    }

    public List<Subscription> adminGetAllSubscriptions() {
        return subscriptionRepository.findAll();
    }

    public List<Lobby> adminGetAllLobbies() {
        return lobbyRepository.findAll();
    }

    public List<ChallengeProgress> adminGetAllChallengeProgress() {
        return challengeProgressRepository.findAll();
    }

    public List<ChallengeProgress> adminGetPendingChallengeProgress() {
        return challengeProgressRepository.findAllByStatus(ProgressStatus.JOINED);
    }

    public List<Kingdom> adminGetAllKingdoms() {
        return kingdomRepository.findAll();
    }

    public Kingdom adminGetKingdomById(Integer kingdomId) {
        Kingdom kingdom = kingdomRepository.findKingdomById(kingdomId);
        if (kingdom == null) {
            throw new ApiException("Kingdom not found");
        }
        return kingdom;
    }
    public void banPlayer(Integer playerId) {
        Player player = playerRepository.findPlayerById(playerId);

        if (player == null) {
            throw new ApiException("Player not found");
        }

        User user = player.getUser();

        if (user == null) {
            throw new ApiException("Player has no user account");
        }

        user.setBanned(true);
        userRepository.save(user);
    }

    public void unbanPlayer(Integer playerId) {
        Player player = playerRepository.findPlayerById(playerId);

        if (player == null) {
            throw new ApiException("Player not found");
        }

        User user = player.getUser();

        if (user == null) {
            throw new ApiException("Player has no user account");
        }

        user.setBanned(false);
        userRepository.save(user);
    }

    //helper method
    private User checkUser(Integer id) {
        User user = userRepository.findUserById(id);
        if (user == null) {
            throw new ApiException("User not found");
        }
        return user;
    }
}