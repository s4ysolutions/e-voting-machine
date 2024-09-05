#
# E-Voting Machine © 2024 by Sergey Dolin is licensed under CC BY-NC-ND 4.0. To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
#

#find . -name '*.lua' -exec lua -lluacov -lluacov.reporter {} \;
find . -name '*.lua' -exec lua {} \;
