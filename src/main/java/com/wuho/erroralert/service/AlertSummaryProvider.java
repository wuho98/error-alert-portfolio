package com.wuho.erroralert.service;

import java.util.Optional;

public interface AlertSummaryProvider {

    Optional<AlertSummaryInput> findAlert(Long alertId);
}
