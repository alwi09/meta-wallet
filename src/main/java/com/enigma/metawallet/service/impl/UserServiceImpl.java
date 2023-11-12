package com.enigma.metawallet.service.impl;

import com.enigma.metawallet.entity.Admin;
import com.enigma.metawallet.entity.User;
import com.enigma.metawallet.entity.UserCredential;
import com.enigma.metawallet.entity.Wallet;
import com.enigma.metawallet.model.request.ChangePasswordRequest;
import com.enigma.metawallet.model.request.UserRequest;
import com.enigma.metawallet.model.request.WalletRequest;
import com.enigma.metawallet.model.response.UserResponse;
import com.enigma.metawallet.model.response.WalletResponse;
import com.enigma.metawallet.repository.UserRepository;
import com.enigma.metawallet.service.AdminService;
import com.enigma.metawallet.service.UserCredentialService;
import com.enigma.metawallet.service.UserService;
import com.enigma.metawallet.service.WalletService;
import com.enigma.metawallet.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final AdminService adminService;
    private final UserCredentialService userCredentialService;
    private final ValidationUtil validationUtil;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User create(User request) {
        validationUtil.validate(request);
        return userRepository.save(request);
    }

    @Override
    public Page<UserResponse> getAllUserForAdmin(Integer size, Integer page) {
        Pageable pageable = PageRequest.of(page, size);

        Page<User> users = userRepository.findAll(pageable);

        List<UserResponse> responses = users.stream().map(this::toUserResponse).collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, users.getTotalElements());
    }

    private UserResponse toUserResponse(User user){
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .city(user.getCity())
                .country(user.getCountry())
                .address(user.getAddress())
                .dateOfBirth(user.getDateOfBirth().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")))
                .email(user.getEmail())
                .username(user.getUserCredential().getUsername())
                .mobilePhone(user.getMobilePhone())
                .build();
    }

    @Override
    public UserResponse getUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not found"));

        return toUserResponse(user);
    }

    @Override
    public WalletResponse getWalletByUserId(String id) {
        Wallet wallet = userRepository.findWalletByUserId(id);
        if (wallet == null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found");
        }

        return WalletResponse.builder()
                .userId(id)
                .walletId(wallet.getId())
                .balance(wallet.getBalance())
                .build();
    }

    @Override
    public WalletResponse topUpWallet(WalletRequest request) {
        validationUtil.validate(request);

        // User
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not found"));

        Wallet userWallet = user.getWallet();
        if (userWallet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet is not found for the user");
        }

        double adminFeePercent = 0.001;
        double adminFeeAmount = request.getBalance() * adminFeePercent; // untuk mendapatkan total biaya admin

        long topUpAmountAfterFee = (long) Math.max(0, Math.round(request.getBalance()) - adminFeeAmount); // request user dikurangi total biaya admin

        Long newUserBalance = userWallet.getBalance() == null ? 0 : userWallet.getBalance() + topUpAmountAfterFee;
        userWallet.setBalance(newUserBalance);
        walletService.update(userWallet);

        // Admin
        Admin admin = adminService.getById(1L);

        Wallet adminWallet = admin.getWallet();
        if (adminWallet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet is not found for the admin");
        }

        Long adminWalletBalance = adminWallet.getBalance();
        Long newAdminBalance = (adminWalletBalance == null ? 0 : adminWalletBalance) + Math.round(adminFeeAmount); // menambahkan hasil total biaya admin dari user request ke wallet admin

        adminWallet.setBalance(newAdminBalance);
        walletService.update(adminWallet);

        return WalletResponse.builder()
                .userId(user.getId())
                .walletId(userWallet.getId())
                .balance(newUserBalance)
                .build();
    }


    @Override
    public UserResponse updateUser(UserRequest request) {
        validationUtil.validate(request);
        User user = userRepository.findById(request.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not found"));

        User build = User.builder()
                .id(request.getId())
                .name(request.getName())
                .city(request.getCity())
                .email(user.getEmail())
                .address(request.getAddress())
                .country(request.getCountry())
                .mobilePhone(request.getMobilePhone())
                .dateOfBirth(user.getDateOfBirth())
                .wallet(user.getWallet())
                .userCredential(user.getUserCredential())
                .build();
        userRepository.save(build);
        return toUserResponse(build);
    }

    @Override
    public void changePassword(String userId, ChangePasswordRequest request) {
        validationUtil.validate(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not found"));

        UserCredential userUserCredential = user.getUserCredential();
        if (userUserCredential == null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User credential is not found for the user");
        }

        if(!passwordEncoder.matches(request.getCurrentPassword(), userUserCredential.getPassword())){ // memeriksa apakah password saat ini sudah cocok
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wrong old password");
        }
        if (!request.getNewPassword().equals(request.getConfirmationPassword())){ // memeriksa apakah password baru dan konfirmasi password baru sudah sesuai
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password and confirmation not same");
        }

        userUserCredential.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userCredentialService.create(userUserCredential);
    }

    @Override
    public void deleteUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not found"));

        userRepository.delete(user);
    }
}
