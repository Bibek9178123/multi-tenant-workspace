package com.multitenant.workspace.service;

import com.multitenant.workspace.dto.AuthDtos;
import com.multitenant.workspace.dto.WorkspaceDtos;
import com.multitenant.workspace.entity.User;
import com.multitenant.workspace.repository.UserRepository;
import com.multitenant.workspace.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final WorkspaceService workspaceService;
    private final EmailService emailService;

    private static final String[] AVATAR_COLORS = {
            "#6366f1", "#8b5cf6", "#ec4899", "#f43f5e", "#f97316",
            "#eab308", "#10b981", "#06b6d4", "#3b82f6"
    };

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider tokenProvider,
                       WorkspaceService workspaceService,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.workspaceService = workspaceService;
        this.emailService = emailService;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email is already registered: " + request.email());
        }

        int colorIdx = Math.abs(request.email().hashCode()) % AVATAR_COLORS.length;
        String color = AVATAR_COLORS[colorIdx];

        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                color
        );
        user = userRepository.save(user);

        // Create default workspace for user
        String defaultWsName = request.workspaceName() != null && !request.workspaceName().isBlank()
                ? request.workspaceName()
                : request.fullName() + "'s Workspace";

        WorkspaceDtos.WorkspaceResponse ws = workspaceService.createWorkspace(
                new WorkspaceDtos.CreateWorkspaceRequest(defaultWsName, null, "Default personal workspace for " + user.getFullName()),
                user.getId(),
                user.getEmail(),
                user.getFullName()
        );

        String token = tokenProvider.generateTokenFromUser(user.getId(), user.getEmail(), user.getFullName());
        AuthDtos.UserProfileResponse profile = new AuthDtos.UserProfileResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getAvatarColor()
        );

        // Send welcome email asynchronously
        emailService.sendWelcomeEmail(user.getEmail(), user.getFullName(), ws.name(), ws.id());

        return new AuthDtos.AuthResponse(token, profile, List.of(ws), ws.id());
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String token = tokenProvider.generateToken(authentication);
        List<WorkspaceDtos.WorkspaceResponse> workspaces = workspaceService.getUserWorkspaces(user.getId());

        String defaultWorkspaceId = workspaces.isEmpty() ? null : workspaces.get(0).id();

        AuthDtos.UserProfileResponse profile = new AuthDtos.UserProfileResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getAvatarColor()
        );

        return new AuthDtos.AuthResponse(token, profile, workspaces, defaultWorkspaceId);
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserProfileResponse getCurrentProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return new AuthDtos.UserProfileResponse(user.getId(), user.getEmail(), user.getFullName(), user.getAvatarColor());
    }
}
