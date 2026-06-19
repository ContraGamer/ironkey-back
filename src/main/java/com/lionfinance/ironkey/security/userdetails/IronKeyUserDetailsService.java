package com.lionfinance.ironkey.security.userdetails;

import com.lionfinance.ironkey.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IronKeyUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    // Usado por Spring Security en el flujo de autenticación por email
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailWithRoles(email)
                .map(IronKeyUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    // Usado por el filtro JWT para cargar el usuario desde el subject del token
    @Transactional(readOnly = true)
    public UserDetails loadUserById(UUID id) {
        return userRepository.findByIdWithRoles(id)
                .map(IronKeyUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
    }
}
