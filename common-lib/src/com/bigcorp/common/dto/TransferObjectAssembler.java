package com.bigcorp.common.dto;

import com.bigcorp.common.model.Client;
import com.bigcorp.common.model.SettlementRecord;
import com.bigcorp.common.model.TradeOrder;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembler that builds Transfer Objects from domain objects and vice versa.
 * 
 * The J2EE patterns book calls this the "Transfer Object Assembler."
 * It's supposed to construct complex Transfer Objects from multiple
 * data sources. In practice it's just a bunch of static methods
 * that call the individual fromXxx() methods.
 *
 * "We need this for the architecture diagram" - the architecture team
 *
 * The Assembler pattern is described in "Core J2EE Patterns" (Alur, Crupi, Malks)
 * chapter 8. We read it on the plane to the architecture offsite in 2001.
 * Most of us fell asleep during the presentation about it.
 *
 * @author Bob
 * @since 1.3
 */
public class TransferObjectAssembler {

    /**
     * Private constructor - all methods are static.
     * (The patterns book says to use a Singleton here but that seems like overkill)
     */
    private TransferObjectAssembler() {
        // utility class
    }

    /**
     * Build an OrderTransferObject with denormalized client name.
     * 
     * This is the "correct" way to build an order DTO - it includes
     * the client name so the UI doesn't have to make a separate call
     * to look it up. In practice, the UI still makes the separate call
     * because nobody told them about this method.
     *
     * @param order the trade order domain object
     * @param client the client domain object (may be null if client lookup failed)
     * @return assembled transfer object with clientName populated
     */
    public static OrderTransferObject assembleOrderTO(TradeOrder order, Client client) {
        OrderTransferObject to = OrderTransferObject.fromTradeOrder(order);
        if (to != null && client != null) {
            to.setClientName(client.getName());
        }
        return to;
    }

    /**
     * Build a list of SettlementTransferObjects from settlement records.
     * 
     * Used by the batch file generator when assembling the nightly
     * settlement file for the clearinghouse. Each record gets its own
     * SettlementTransferObject with clearinghouse-specific fields populated.
     *
     * @param records list of SettlementRecord domain objects
     * @return list of SettlementTransferObject instances
     */
    public static List assembleSettlementBatch(List records) {
        List result = new ArrayList();

        if (records == null) {
            return result;
        }

        for (int i = 0; i < records.size(); i++) {
            Object obj = records.get(i);
            if (obj instanceof SettlementRecord) {
                SettlementRecord record = (SettlementRecord) obj;
                SettlementTransferObject to = SettlementTransferObject.fromSettlementRecord(record);
                if (to != null) {
                    result.add(to);
                }
            }
        }

        return result;
    }
}
