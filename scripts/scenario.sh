#!/bin/sh

### Network scenario description
#     [ctx]         
#                              [s1]
#                   |           |           |
#                  [s2]        [s3]        [s4]
#               |   |   |    |  |   |     |   |  |   
#             [h1][h1][h2] [h4][h5][h6] [h7][h8][h9]

#  ctx = controller
#  h* = 10.0.0.*
#  m  = 239.1.1.1

sudo mn --topo tree,depth=2,fanout=3 --mac --switch ovsk --controller remote,ip=127.0.0.1,port=6653,protocols=OpenFlow13 --ipbase=10.0.0.0
