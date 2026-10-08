package com.example.ticketsystem.worker.service;

import com.example.ticketsystem.worker.model.TicketOrderMessage;
import com.example.ticketsystem.worker.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderProcessingServiceTest {
    @Test void sameUserCannotCreateAnotherOrderForSameEvent() {
        OrderRepository repo = mock(OrderRepository.class);
        when(repo.existsByEventAndUser(1L, "u")).thenReturn(true);
        var service = new OrderProcessingService(repo);
        assertEquals(OrderProcessingService.ProcessResult.ORDER_ALREADY_EXISTS,
                service.process(new TicketOrderMessage("m", 1L, "u", "now")));
        verify(repo, never()).decrementStockAtomic(anyLong());
        verify(repo, never()).insertOrder(anyLong(), anyString(), anyString());
    }
    @Test void normalMessageChecksDuplicatesThenCreatesOrder() {
        OrderRepository repo = mock(OrderRepository.class);
        when(repo.decrementStockAtomic(1L)).thenReturn(1);
        var service = new OrderProcessingService(repo);
        assertEquals(OrderProcessingService.ProcessResult.CREATED, service.process(new TicketOrderMessage("m", 1L, "u", "now")));
        InOrder order = inOrder(repo);
        order.verify(repo).existsByMessageId("m"); order.verify(repo).existsByEventAndUser(1L, "u");
        order.verify(repo).decrementStockAtomic(1L); order.verify(repo).insertOrder(1L, "u", "m");
    }
    @Test void duplicateMessagesStillDoNotDeductStockAgain() {
        OrderRepository repo = mock(OrderRepository.class);
        when(repo.existsByMessageId("m")).thenReturn(true);
        assertEquals(OrderProcessingService.ProcessResult.ALREADY_PROCESSED,
                new OrderProcessingService(repo).process(new TicketOrderMessage("m", 1L, "u", "now")));
        verify(repo, never()).decrementStockAtomic(anyLong());
    }
}
