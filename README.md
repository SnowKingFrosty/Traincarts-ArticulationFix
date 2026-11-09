# Traincarts-ArticulationFix

Name the item attachments `LinkA` and `LinkB`. `LinkA` connects to the nearest
`LinkAConnection` attachment on the preceding cart, and `LinkB` connects to the
nearest `LinkBConnection` on the following cart. Connections must be no more
than three blocks from the link attachment, inclusive.

The link item is centered between its attachment position and the connection,
and scaled along its existing local Z axis. Set `base-length` to the model's
length at scale 1; `min-scale` and `max-scale` limit the resulting scale. The
item's orientation is not changed. When no connection is in range, the item
returns to its original position and scale.

Scaling requires a TrainCarts item display entity. Armor-stand-only item
attachments and clients that do not support display entities cannot be scaled.