package com.smartparking.controller;

import com.smartparking.dto.ProcessResponse;
import com.smartparking.dto.WaitingRequestCreate;
import com.smartparking.dto.WaitingResponse;
import com.smartparking.service.WaitingListProcessor;
import com.smartparking.service.WaitingListService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/waiting-list")
public class WaitingListController {

    private final WaitingListService waitingListService;
    private final WaitingListProcessor waitingListProcessor;

    public WaitingListController(WaitingListService waitingListService,
                                 WaitingListProcessor waitingListProcessor) {
        this.waitingListService = waitingListService;
        this.waitingListProcessor = waitingListProcessor;
    }

    @GetMapping
    public List<WaitingResponse> list() {
        return waitingListService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WaitingResponse join(@Valid @RequestBody WaitingRequestCreate request) {
        return waitingListService.enqueue(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        waitingListService.cancel(id);
    }

    /**
     * Demo button: try to allocate every waiting request right now.
     */
    @PostMapping("/process")
    public ProcessResponse process() {
        return new ProcessResponse(waitingListProcessor.processWaitingList());
    }
}
