INSERT INTO mkt_journey_enrollment (
    id, journey_id, lead_id, current_node_id, status, enrolled_at, updated_at
) VALUES (
    COALESCE(:id::uuid, gen_random_uuid()),
    :journey_id::uuid,
    :lead_id::uuid,
    :current_node_id,
    COALESCE(:status, 'ACTIVE'),
    now(),
    now()
);
