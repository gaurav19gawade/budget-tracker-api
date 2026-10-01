package com.budgettracker.api;

import com.budgettracker.application.UserProvisioner;
import com.budgettracker.application.identity.AuthenticatedPrincipal;
import com.budgettracker.application.identity.HouseholdContext;
import com.budgettracker.application.port.CurrentUserProvider;
import com.budgettracker.application.port.HouseholdRepository;
import com.budgettracker.domain.error.NoHouseholdException;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Single place where "who is calling and which household do they act for" is decided.
 * Controllers declare an {@link AuthenticatedPrincipal} or {@link HouseholdContext}
 * parameter and never touch the token themselves.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final CurrentUserProvider currentUser;
    private final UserProvisioner provisioner;
    private final HouseholdRepository households;

    public CurrentUserArgumentResolver(CurrentUserProvider currentUser,
                                       UserProvisioner provisioner,
                                       HouseholdRepository households) {
        this.currentUser = currentUser;
        this.provisioner = provisioner;
        this.households = households;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        return AuthenticatedPrincipal.class.equals(type) || HouseholdContext.class.equals(type);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        AuthenticatedPrincipal principal = currentUser.require();
        provisioner.ensure(principal);

        if (AuthenticatedPrincipal.class.equals(parameter.getParameterType())) {
            return principal;
        }
        UUID householdId = households.findHouseholdIdOfUser(principal.userId())
                .orElseThrow(NoHouseholdException::new);
        return new HouseholdContext(principal.userId(), householdId);
    }
}
