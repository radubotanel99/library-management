package com.library.management.backend.member;

import com.library.management.backend.common.dto.PagedResponse;
import com.library.management.backend.member.dto.MemberRequest;
import com.library.management.backend.member.dto.MemberResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Member endpoints ({@code API_CONTRACT.md} §7).
 *
 * <p>Pure HTTP plumbing: bind, delegate, map the result onto a status code. No
 * business rules and no try/catch -- rule violations arrive as
 * {@code ApiException} and are turned into responses by the single
 * {@code @RestControllerAdvice}.
 *
 * <p>The list is paged, like the catalogue and unlike categories: a library has
 * thousands of members. Which sort properties are actually honoured is a service
 * decision, not a binding one, so the {@link Pageable} is passed through untouched.
 */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public PagedResponse<MemberResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return memberService.list(search, pageable);
    }

    /** Active members only: an archived member is reported as missing. */
    @GetMapping("/{id}")
    public MemberResponse findById(@PathVariable Long id) {
        return memberService.findById(id);
    }

    @PostMapping
    public ResponseEntity<MemberResponse> create(@Valid @RequestBody MemberRequest request) {
        MemberResponse created = memberService.create(request);
        return ResponseEntity
                .created(URI.create("/api/members/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public MemberResponse update(@PathVariable Long id, @Valid @RequestBody MemberRequest request) {
        return memberService.update(id, request);
    }

    /** Archives the member; soft delete, and there is no restore endpoint by design. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        memberService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
